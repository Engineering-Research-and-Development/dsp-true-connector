package it.eng.dcp.service;

import it.eng.dcp.model.KeyMetadata;
import it.eng.dcp.repository.KeyMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeyMetadataServiceTest {
    private KeyMetadataRepository repository;
    private KeyMetadataService service;

    @BeforeEach
    void setUp() {
        repository = mock(KeyMetadataRepository.class);
        service = new KeyMetadataService(repository);
    }

    @Test
    @DisplayName("saveNewKeyMetadata deactivates previous active key and saves new active key")
    void saveNewKeyMetadata_deactivatesPreviousAndSavesNew() {
        KeyMetadata previous = KeyMetadata.Builder.newInstance()
                .alias("old")
                .createdAt(Instant.now().minusSeconds(3600))
                .active(true)
                .archived(false)
                .build();

        when(repository.findByActiveTrue()).thenReturn(Optional.of(previous));
        when(repository.save(any(KeyMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KeyMetadata result = service.saveNewKeyMetadata("newAlias");

        assertEquals("newAlias", result.getAlias());
        assertTrue(result.isActive());
        assertNotNull(result.getCreatedAt());

        ArgumentCaptor<KeyMetadata> captor = ArgumentCaptor.forClass(KeyMetadata.class);
        verify(repository, times(2)).save(captor.capture());
        List<KeyMetadata> saved = captor.getAllValues();
        KeyMetadata archivedSaved = saved.get(0);
        KeyMetadata newSaved = saved.get(1);

        // archived record should keep the same id as previous
        assertEquals(previous.getId(), archivedSaved.getId());
        assertFalse(archivedSaved.isActive());
        assertTrue(archivedSaved.isArchived());
        assertNotNull(archivedSaved.getArchivedAt());

        // new record should have a different id and be active
        assertNotEquals(archivedSaved.getId(), newSaved.getId());
        assertEquals("newAlias", newSaved.getAlias());
        assertTrue(newSaved.isActive());
        assertFalse(newSaved.isArchived());
    }

    @Test
    @DisplayName("saveNewKeyMetadata saves new key as active when no previous active key exists")
    void saveNewKeyMetadata_noPreviousActiveKey() {
        when(repository.findByActiveTrue()).thenReturn(Optional.empty());
        when(repository.save(any(KeyMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KeyMetadata result = service.saveNewKeyMetadata("alias1");

        assertEquals("alias1", result.getAlias());
        assertTrue(result.isActive());
        assertNotNull(result.getCreatedAt());
        verify(repository, times(1)).save(any(KeyMetadata.class));
    }

    @Test
    @DisplayName("getActiveKeyMetadata returns active key if present")
    void getActiveKeyMetadata_returnsActive() {
        KeyMetadata meta = KeyMetadata.Builder.newInstance().alias("a").active(true).build();
        when(repository.findByActiveTrue()).thenReturn(Optional.of(meta));
        Optional<KeyMetadata> result = service.getActiveKeyMetadata();
        assertTrue(result.isPresent());
        assertSame(meta, result.get());
    }

    @Test
    @DisplayName("getActiveKeyMetadata returns empty if no active key")
    void getActiveKeyMetadata_returnsEmpty() {
        when(repository.findByActiveTrue()).thenReturn(Optional.empty());
        Optional<KeyMetadata> result = service.getActiveKeyMetadata();
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("getKeyMetadataByAlias returns key if found")
    void getKeyMetadataByAlias_found() {
        KeyMetadata meta = KeyMetadata.Builder.newInstance().alias("aliasX").build();
        when(repository.findByAlias("aliasX")).thenReturn(Optional.of(meta));
        Optional<KeyMetadata> result = service.getKeyMetadataByAlias("aliasX");
        assertTrue(result.isPresent());
        assertSame(meta, result.get());
    }

    @Test
    @DisplayName("getKeyMetadataByAlias returns empty if not found")
    void getKeyMetadataByAlias_notFound() {
        when(repository.findByAlias("aliasY")).thenReturn(Optional.empty());
        Optional<KeyMetadata> result = service.getKeyMetadataByAlias("aliasY");
        assertFalse(result.isPresent());
    }
}
