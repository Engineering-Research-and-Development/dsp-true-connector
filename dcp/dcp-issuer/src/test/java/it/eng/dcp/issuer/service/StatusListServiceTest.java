package it.eng.dcp.issuer.service;

import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.issuer.config.IssuerProperties;
import it.eng.dcp.issuer.model.StatusList;
import it.eng.dcp.issuer.repository.StatusListRepository;
import it.eng.dcp.issuer.service.jwt.StatusListCredentialGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusListServiceTest {

    private static final String ISSUER_DID = "did:web:issuer.example.com";
    private static final String BASE_URL = "http://issuer.example.com:8084";
    private static final String PURPOSE = StatusListService.REVOCATION_PURPOSE;

    @Mock
    private StatusListRepository statusListRepository;

    @Mock
    private StatusListCredentialGenerator credentialGenerator;

    private IssuerProperties issuerProperties;
    private StatusListService statusListService;

    @BeforeEach
    void setUp() {
        issuerProperties = new IssuerProperties();
        issuerProperties.setBaseUrl(BASE_URL);
        statusListService = new StatusListService(statusListRepository, issuerProperties, credentialGenerator);
    }

    @Test
    @DisplayName("The first allocation creates a signed list at a resolvable URL and leaves the bit at 0")
    void allocateStatusEntry_createsNewList() {
        when(statusListRepository.findFirstByIssuerDidAndStatusPurposeAndProfileOrderByCreatedAtDesc(
                ISSUER_DID, PURPOSE, ProfileId.VC20_BSSL_JWT)).thenReturn(Optional.empty());
        when(statusListRepository.save(any(StatusList.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(credentialGenerator.generate(any())).thenReturn("signed-jwt");

        StatusListService.StatusListEntry entry =
                statusListService.allocateStatusEntry(ISSUER_DID, PURPOSE, ProfileId.VC20_BSSL_JWT);

        StatusList saved = captureSaved();
        assertEquals(0, entry.index());
        assertEquals(BASE_URL + "/issuer/status/" + saved.getId(), entry.statusListCredential());
        assertEquals(entry.statusListCredential(), saved.getUrl());
        assertEquals(ProfileId.VC20_BSSL_JWT, saved.getProfile());
        assertEquals(ISSUER_DID, saved.getIssuerDid());
        assertEquals(StatusListService.DEFAULT_STATUS_LIST_SIZE, saved.getSize());
        assertEquals(1, saved.getNextIndex());
        assertFalse(saved.isSet(0));
        assertEquals("signed-jwt", saved.getSignedCredential());
    }

    @Test
    @DisplayName("A trailing slash in the base URL does not double the slash of the status list URL")
    void allocateStatusEntry_trailingSlashBaseUrl() {
        issuerProperties.setBaseUrl(BASE_URL + "/");
        when(statusListRepository.findFirstByIssuerDidAndStatusPurposeAndProfileOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(statusListRepository.save(any(StatusList.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StatusListService.StatusListEntry entry =
                statusListService.allocateStatusEntry(ISSUER_DID, PURPOSE, ProfileId.VC11_SL2021_JWT);

        assertTrue(entry.statusListCredential().startsWith(BASE_URL + "/issuer/status/"));
    }

    @Test
    @DisplayName("Allocation continues the existing list and does not re-sign it, because no status bit changed")
    void allocateStatusEntry_existingList_nextIndex() {
        StatusList existing = list(ProfileId.VC11_SL2021_JWT, 16, 5);
        when(statusListRepository.findFirstByIssuerDidAndStatusPurposeAndProfileOrderByCreatedAtDesc(
                ISSUER_DID, PURPOSE, ProfileId.VC11_SL2021_JWT)).thenReturn(Optional.of(existing));
        when(statusListRepository.save(existing)).thenReturn(existing);

        StatusListService.StatusListEntry entry =
                statusListService.allocateStatusEntry(ISSUER_DID, PURPOSE, ProfileId.VC11_SL2021_JWT);

        assertEquals(5, entry.index());
        assertEquals(existing.getUrl(), entry.statusListCredential());
        assertEquals(6, existing.getNextIndex());
        assertFalse(existing.isSet(5));
        verifyNoInteractions(credentialGenerator);
    }

    @Test
    @DisplayName("Indices are not reused: a revoked index stays allocated and the next allocation moves on")
    void allocateStatusEntry_revokedIndexNotReused() {
        StatusList existing = list(ProfileId.VC20_BSSL_JWT, 16, 2);
        existing.setBit(1);
        when(statusListRepository.findFirstByIssuerDidAndStatusPurposeAndProfileOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(Optional.of(existing));
        when(statusListRepository.save(existing)).thenReturn(existing);

        StatusListService.StatusListEntry entry =
                statusListService.allocateStatusEntry(ISSUER_DID, PURPOSE, ProfileId.VC20_BSSL_JWT);

        assertEquals(2, entry.index());
        assertTrue(existing.isSet(1));
    }

    @Test
    @DisplayName("A full list makes allocation start a new list")
    void allocateStatusEntry_fullList_createsNewList() {
        StatusList full = list(ProfileId.VC20_BSSL_JWT, 8, 8);
        when(statusListRepository.findFirstByIssuerDidAndStatusPurposeAndProfileOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(Optional.of(full));
        when(statusListRepository.save(any(StatusList.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StatusListService.StatusListEntry entry =
                statusListService.allocateStatusEntry(ISSUER_DID, PURPOSE, ProfileId.VC20_BSSL_JWT);

        assertEquals(0, entry.index());
        assertNotEquals(full.getUrl(), entry.statusListCredential());
        assertEquals(8, full.getNextIndex());
    }

    @Test
    @DisplayName("The service refuses to start without a base URL, because the list URLs would not resolve")
    void constructor_noBaseUrl_throws() {
        IssuerProperties noBaseUrl = new IssuerProperties();
        noBaseUrl.setBaseUrl(" ");

        assertThrows(IllegalStateException.class,
                () -> new StatusListService(statusListRepository, noBaseUrl, credentialGenerator));
        noBaseUrl.setBaseUrl(null);
        assertThrows(IllegalStateException.class,
                () -> new StatusListService(statusListRepository, noBaseUrl, credentialGenerator));
    }

    @Test
    @DisplayName("Allocation retries after a concurrent update of the list")
    void allocateStatusEntry_optimisticLockConflict_retries() {
        when(statusListRepository.findFirstByIssuerDidAndStatusPurposeAndProfileOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(Optional.of(list(ProfileId.VC20_BSSL_JWT, 16, 3)))
                .thenReturn(Optional.of(list(ProfileId.VC20_BSSL_JWT, 16, 4)));
        when(statusListRepository.save(any(StatusList.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StatusListService.StatusListEntry entry =
                statusListService.allocateStatusEntry(ISSUER_DID, PURPOSE, ProfileId.VC20_BSSL_JWT);

        assertEquals(4, entry.index());
        verify(statusListRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Allocation gives up after repeated concurrent updates")
    void allocateStatusEntry_persistentConflict_throws() {
        when(statusListRepository.findFirstByIssuerDidAndStatusPurposeAndProfileOrderByCreatedAtDesc(any(), any(), any()))
                .thenAnswer(invocation -> Optional.of(list(ProfileId.VC20_BSSL_JWT, 16, 3)));
        when(statusListRepository.save(any(StatusList.class))).thenThrow(new OptimisticLockingFailureException("conflict"));

        assertThrows(IllegalStateException.class,
                () -> statusListService.allocateStatusEntry(ISSUER_DID, PURPOSE, ProfileId.VC20_BSSL_JWT));
    }

    @Test
    @DisplayName("Revoke sets the bit to 1 and re-signs the list")
    void revoke_setsBitAndResigns() {
        StatusList existing = list(ProfileId.VC20_BSSL_JWT, 16, 3);
        when(statusListRepository.findById("list-1")).thenReturn(Optional.of(existing));
        when(credentialGenerator.generate(existing)).thenReturn("re-signed-jwt");

        assertTrue(statusListService.revoke("list-1", 2));

        StatusList saved = captureSaved();
        assertTrue(saved.isSet(2));
        assertFalse(saved.isSet(1));
        assertEquals("re-signed-jwt", saved.getSignedCredential());
    }

    @Test
    @DisplayName("Revoking an already revoked entry changes nothing")
    void revoke_alreadyRevoked_noChange() {
        StatusList existing = list(ProfileId.VC20_BSSL_JWT, 16, 3);
        existing.setBit(2);
        when(statusListRepository.findById("list-1")).thenReturn(Optional.of(existing));

        assertFalse(statusListService.revoke("list-1", 2));

        verify(statusListRepository, never()).save(any());
        verifyNoInteractions(credentialGenerator);
    }

    @Test
    @DisplayName("Revoke rejects an unknown list")
    void revoke_unknownList_throws() {
        when(statusListRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> statusListService.revoke("missing", 0));
    }

    @Test
    @DisplayName("Revoke rejects a blank list ID without querying the repository")
    void revoke_blankListId_throws() {
        assertThrows(NoSuchElementException.class, () -> statusListService.revoke(" ", 0));
        verifyNoInteractions(statusListRepository);
    }

    @Test
    @DisplayName("Revoke rejects an index that was never allocated")
    void revoke_unallocatedIndex_throws() {
        when(statusListRepository.findById("list-1")).thenReturn(Optional.of(list(ProfileId.VC20_BSSL_JWT, 16, 3)));

        assertThrows(IllegalArgumentException.class, () -> statusListService.revoke("list-1", 3));
        assertThrows(IllegalArgumentException.class, () -> statusListService.revoke("list-1", -1));
        verify(statusListRepository, never()).save(any());
    }

    @Test
    @DisplayName("Publishing returns the stored credential when it is signed with the current key")
    void publishStatusList_currentKey_returnsStored() {
        StatusList existing = list(ProfileId.VC11_SL2021_JWT, 16, 1);
        existing.setSignedCredential("stored-jwt");
        when(statusListRepository.findById("list-1")).thenReturn(Optional.of(existing));
        when(credentialGenerator.isSignedWithCurrentKey("stored-jwt")).thenReturn(true);

        StatusListService.PublishedStatusList published = statusListService.publishStatusList("list-1").orElseThrow();

        assertEquals("stored-jwt", published.credential());
        assertEquals(StatusListCredentialGenerator.VC11_MEDIA_TYPE, published.mediaType());
        verify(statusListRepository, never()).save(any());
    }

    @Test
    @DisplayName("Publishing re-signs the credential when it is missing or signed with a rotated key")
    void publishStatusList_staleKey_resigns() {
        StatusList existing = list(ProfileId.VC20_BSSL_JWT, 16, 1);
        existing.setSignedCredential("old-key-jwt");
        when(statusListRepository.findById("list-1")).thenReturn(Optional.of(existing));
        when(credentialGenerator.isSignedWithCurrentKey("old-key-jwt")).thenReturn(false);
        when(credentialGenerator.generate(existing)).thenReturn("new-key-jwt");
        when(statusListRepository.save(existing)).thenReturn(existing);

        StatusListService.PublishedStatusList published = statusListService.publishStatusList("list-1").orElseThrow();

        assertEquals("new-key-jwt", published.credential());
        assertEquals(StatusListCredentialGenerator.VC20_MEDIA_TYPE, published.mediaType());
    }

    @Test
    @DisplayName("Publishing an unknown list returns empty")
    void publishStatusList_unknownList_empty() {
        when(statusListRepository.findById("missing")).thenReturn(Optional.empty());

        assertTrue(statusListService.publishStatusList("missing").isEmpty());
        assertTrue(statusListService.publishStatusList(null).isEmpty());
    }

    private StatusList captureSaved() {
        ArgumentCaptor<StatusList> captor = ArgumentCaptor.forClass(StatusList.class);
        verify(statusListRepository).save(captor.capture());
        return captor.getValue();
    }

    private static StatusList list(ProfileId profile, int size, int nextIndex) {
        return StatusList.Builder.newInstance()
                .id("list-1")
                .url(BASE_URL + "/issuer/status/list-1")
                .issuerDid(ISSUER_DID)
                .statusPurpose(PURPOSE)
                .profile(profile)
                .size(size)
                .nextIndex(nextIndex)
                .build();
    }
}
