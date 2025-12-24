package it.eng.dcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.CredentialStatus;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.core.ProfileResolver;
import it.eng.dcp.model.*;
import it.eng.dcp.repository.CredentialStatusRepository;
import it.eng.dcp.repository.VerifiableCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HolderServiceTest {

    @Mock
    private SelfIssuedIdTokenService tokenService;

    @Mock
    private PresentationService presentationService;

    @Mock
    private PresentationRateLimiter rateLimiter;

    @Mock
    private VerifiableCredentialRepository credentialRepository;

    @Mock
    private CredentialStatusRepository credentialStatusRepository;

    @Mock
    private ProfileResolver profileResolver;

    @Mock
    private ObjectMapper mapper;

    private HolderService holderService;

    @BeforeEach
    void setUp() {
        holderService = new HolderService(
                tokenService,
                presentationService,
                rateLimiter,
                credentialRepository,
                credentialStatusRepository,
                profileResolver,
                mapper
        );
    }

    @Test
    void authorizePresentationQuery_validToken_success() {
        // Arrange
        String bearerToken = "valid-token";
        String holderDid = "did:example:holder123";

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(holderDid)
                .build();

        when(tokenService.validateToken(bearerToken)).thenReturn(claims);

        // Act
        JWTClaimsSet result = holderService.authorizePresentationQuery(bearerToken);

        // Assert
        assertNotNull(result);
        assertEquals(holderDid, result.getSubject());
        verify(tokenService).validateToken(bearerToken);
    }

    @Test
    void authorizePresentationQuery_nullToken_throwsSecurityException() {
        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class,
                () -> holderService.authorizePresentationQuery(null));

        assertEquals("Bearer token is required", exception.getMessage());
        verify(tokenService, never()).validateToken(anyString());
    }

    @Test
    void authorizePresentationQuery_blankToken_throwsSecurityException() {
        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class,
                () -> holderService.authorizePresentationQuery("   "));

        assertEquals("Bearer token is required", exception.getMessage());
        verify(tokenService, never()).validateToken(anyString());
    }

    @Test
    void checkRateLimit_allowed_returnsTrue() {
        // Arrange
        String holderDid = "did:example:holder123";
        when(rateLimiter.tryConsume(holderDid)).thenReturn(true);

        // Act
        boolean result = holderService.checkRateLimit(holderDid);

        // Assert
        assertTrue(result);
        verify(rateLimiter).tryConsume(holderDid);
    }

    @Test
    void checkRateLimit_exceeded_returnsFalse() {
        // Arrange
        String holderDid = "did:example:holder123";
        when(rateLimiter.tryConsume(holderDid)).thenReturn(false);

        // Act
        boolean result = holderService.checkRateLimit(holderDid);

        // Assert
        assertFalse(result);
        verify(rateLimiter).tryConsume(holderDid);
    }

    @Test
    void createPresentation_success() {
        // Arrange
        PresentationQueryMessage query = mock(PresentationQueryMessage.class);
        String holderDid = "did:example:holder123";
        PresentationResponseMessage expectedResponse = mock(PresentationResponseMessage.class);

        when(presentationService.createPresentation(query)).thenReturn(expectedResponse);

        // Act
        PresentationResponseMessage result = holderService.createPresentation(query, holderDid);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResponse, result);
        verify(presentationService).createPresentation(query);
    }

    @Test
    void authorizeIssuer_validToken_success() {
        // Arrange
        String bearerToken = "valid-token";
        String issuerDid = "did:example:issuer123";

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuerDid)
                .build();

        when(tokenService.validateToken(bearerToken)).thenReturn(claims);

        // Act
        String result = holderService.authorizeIssuer(bearerToken);

        // Assert
        assertEquals(issuerDid, result);
        verify(tokenService).validateToken(bearerToken);
    }

    @Test
    void authorizeIssuer_nullToken_throwsSecurityException() {
        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class,
                () -> holderService.authorizeIssuer(null));

        assertEquals("Bearer token is required", exception.getMessage());
        verify(tokenService, never()).validateToken(anyString());
    }

    @Test
    void authorizeIssuer_missingIssuerClaim_throwsSecurityException() {
        // Arrange
        String bearerToken = "valid-token";
        JWTClaimsSet claims = new JWTClaimsSet.Builder().build();

        when(tokenService.validateToken(bearerToken)).thenReturn(claims);

        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class,
                () -> holderService.authorizeIssuer(bearerToken));

        assertEquals("Invalid token: missing issuer claim", exception.getMessage());
    }

    @Test
    void processIssuedCredentials_emptyCredentials_throwsException() {
        // Arrange
        CredentialMessage msg = CredentialMessage.Builder.newInstance()
                .status("ISSUED")
                .issuerPid("issuer-123")
                .holderPid("did:example:holder")
                .credentials(List.of(CredentialMessage.CredentialContainer.Builder.newInstance()
                        .credentialType("test")
                        .format("jwt")
                        .payload("test")
                        .build()))
                .build();

        // Clear credentials to make it empty (validation passes during build but we test the service check)
        msg.getCredentials().clear();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> holderService.processIssuedCredentials(msg, "did:example:issuer"));

        assertEquals("ISSUED status requires non-empty credentials array", exception.getMessage());
    }

    @Test
    void processIssuedCredentials_nullCredentials_throwsException() {
        // Arrange - Create a mock message since builder validation prevents null credentials
        CredentialMessage msg = mock(CredentialMessage.class);
        when(msg.getCredentials()).thenReturn(null);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> holderService.processIssuedCredentials(msg, "did:example:issuer"));

        assertEquals("ISSUED status requires non-empty credentials array", exception.getMessage());
    }

    @Test
    void processRejectedCredentials_success() {
        // Arrange
        String requestId = "req-123";
        CredentialMessage msg = CredentialMessage.Builder.newInstance()
                .status("REJECTED")
                .issuerPid(requestId)
                .holderPid("did:example:holder")
                .rejectionReason("Test rejection")
                .credentials(List.of(CredentialMessage.CredentialContainer.Builder.newInstance()
                        .credentialType("test")
                        .format("jwt")
                        .payload("test")
                        .build()))
                .build();

        // Act
        HolderService.CredentialReceptionResult result = holderService.processRejectedCredentials(msg);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getSavedCount());
        assertEquals(0, result.getSkippedCount());
        assertFalse(result.hasSkipped());
        assertFalse(result.isEmpty());

        ArgumentCaptor<CredentialStatusRecord> captor = ArgumentCaptor.forClass(CredentialStatusRecord.class);
        verify(credentialStatusRepository).save(captor.capture());

        CredentialStatusRecord saved = captor.getValue();
        assertEquals(CredentialStatus.REJECTED, saved.getStatus());
        assertEquals("Test rejection", saved.getRejectionReason());
    }

    @Test
    void processCredentialOffer_validOffer_success() {
        // Arrange
        CredentialOfferMessage.OfferedCredential credential1 = CredentialOfferMessage.OfferedCredential.Builder.newInstance()
                .credentialType("MembershipCredential")
                .format("jwt")
                .build();

        CredentialOfferMessage offer = CredentialOfferMessage.Builder.newInstance()
                .type("CredentialOfferMessage")
                .offeredCredentials(List.of(credential1))
                .build();

        // Act
        boolean result = holderService.processCredentialOffer(offer);

        // Assert
        assertTrue(result);
    }

    @Test
    void processCredentialOffer_nullOffer_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> holderService.processCredentialOffer(null));

        assertEquals("offeredCredentials must be provided and non-empty", exception.getMessage());
    }

    @Test
    void processCredentialOffer_emptyOfferedCredentials_throwsException() {
        // Arrange - Use mock since builder validation prevents empty credentials
        CredentialOfferMessage offer = mock(CredentialOfferMessage.class);
        when(offer.getOfferedCredentials()).thenReturn(Collections.emptyList());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> holderService.processCredentialOffer(offer));

        assertEquals("offeredCredentials must be provided and non-empty", exception.getMessage());
    }

    @Test
    void credentialReceptionResult_isEmpty_whenAllSkipped() {
        // Arrange
        HolderService.CredentialReceptionResult result = new HolderService.CredentialReceptionResult(0, 5);

        // Assert
        assertTrue(result.isEmpty());
        assertTrue(result.hasSkipped());
        assertEquals(0, result.getSavedCount());
        assertEquals(5, result.getSkippedCount());
    }

    @Test
    void credentialReceptionResult_notEmpty_whenSomeSaved() {
        // Arrange
        HolderService.CredentialReceptionResult result = new HolderService.CredentialReceptionResult(3, 2);

        // Assert
        assertFalse(result.isEmpty());
        assertTrue(result.hasSkipped());
        assertEquals(3, result.getSavedCount());
        assertEquals(2, result.getSkippedCount());
    }

    @Test
    void credentialReceptionResult_noSkipped_whenAllSaved() {
        // Arrange
        HolderService.CredentialReceptionResult result = new HolderService.CredentialReceptionResult(5, 0);

        // Assert
        assertFalse(result.isEmpty());
        assertFalse(result.hasSkipped());
        assertEquals(5, result.getSavedCount());
        assertEquals(0, result.getSkippedCount());
    }

    @Test
    @Disabled("Disabled until full VC parsing and validation is implemented")
    void processIssuedCredentials_validIssuedMessage_savesCredentialsAndReturnsSuccess() {
        // Arrange
        String issuerPid = "39c7157d-99ba-4596-ac44-741f415d6646";
        String holderPid = "59e3cc57-8773-4aa6-87e5-4bc47f09f429";
        String requestId = holderPid;
        String payload1 = "eyJraWQiOiJkaWQ6d2ViOmxvY2FsaG9zdCUzQTgwODA6aXNzdWVyIzY2ZjcxMDI5LTg3MDYtNDk5Ny1iNTZjLTJmZjRhODQzYjZkYSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiIxNzMyODc3ZS0zNmM4LTQwYjgtYmZjYy1jZDU5YmFmMzdiMjciLCJuYmYiOjE3NjY1Njk5MjgsImlzcyI6ImRpZDp3ZWI6bG9jYWxob3N0JTNBODA4MDppc3N1ZXIiLCJleHAiOjE3NjY1NzAyMjgsImlhdCI6MTc2NjU2OTkyOCwidmMiOnsiQGNvbnRleHQiOiIxNzMyODc3ZS0zNmM4LTQwYjgtYmZjYy1jZDU5YmFmMzdiMjciLCJpZCI6IjE3MzI4NzdlLTM2YzgtNDBiOC1iZmNjLWNkNTliYWYzN2IyNyIsInR5cGUiOlsiTWVtYmVyc2hpcENyZWRlbnRpYWwiXSwiaXNzdWVyIjoiZGlkOndlYjpsb2NhbGhvc3QlM0E4MDgwOmlzc3VlciIsImlzc3VhbmNlRGF0ZSI6IjIwMjUtMTItMjRUMDk6NTI6MDguNjkzMDg3MjAwWiIsImV4cGlyYXRpb25EYXRlIjpudWxsLCJjcmVkZW50aWFsU3ViamVjdCI6eyJmb28iOiJiYXIiLCJpZCI6ImRpZDp3ZWI6bG9jYWxob3N0JTNBODA4MDpob2xkZXIifX0sImp0aSI6ImIzNDBjYmVlLWNjZDAtNDc1Mi1hNzFlLTBhZTkxZGQzZGY2MyJ9.wE4Um0edixt9rKKBYv_vrquOHW6S23tCB_EQMeUZdGqkxeQbBsigbScDu3fRLOas6udN2vj6517Kg9gRqv5nVw";
        String payload2 = "eyJraWQiOiJkaWQ6d2ViOmxvY2FsaG9zdCUzQTgwODA6aXNzdWVyIzY2ZjcxMDI5LTg3MDYtNDk5Ny1iNTZjLTJmZjRhODQzYjZkYSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiI4ZGViYWM1Mi0wYWUxLTQwZTgtYWY4Ni1jYzY1MGYyNjNlMDMiLCJuYmYiOjE3NjY1Njk5MjgsImlzcyI6ImRpZDp3ZWI6bG9jYWxob3N0JTNBODA4MDppc3N1ZXIiLCJleHAiOjE3NjY1NzAyMjgsImlhdCI6MTc2NjU2OTkyOCwidmMiOnsiQGNvbnRleHQiOiI4ZGViYWM1Mi0wYWUxLTQwZTgtYWY4Ni1jYzY1MGYyNjNlMDMiLCJpZCI6IjhkZWJhYzUyLTBhZTEtNDBlOC1hZjg2LWNjNjUwZjI2M2UwMyIsInR5cGUiOlsiU2Vuc2l0aXZlRGF0YUNyZWRlbnRpYWwiXSwiaXNzdWVyIjoiZGlkOndlYjpsb2NhbGhvc3QlM0E4MDgwOmlzc3VlciIsImlzc3VhbmNlRGF0ZSI6IjIwMjUtMTItMjRUMDk6NTI6MDguNzYwNzcxNzAwWiIsImV4cGlyYXRpb25EYXRlIjpudWxsLCJjcmVkZW50aWFsU3ViamVjdCI6eyJmb28iOiJiYXIiLCJpZCI6ImRpZDp3ZWI6bG9jYWxob3N0JTNBODA4MDpob2xkZXIifX0sImp0aSI6IjQ3M2ExZjIwLWYzNGMtNGI3OS1hMTFhLTM4ZTg2MWQzYWM4MiJ9.EcbmqIlr6o_zvvyyRS-NdFr7uNH3KVR0ZhD1h4U0sKPSgLdwHIQiR6KAkZwGq7Fpu7ZSTfGTM6b5E2AORbSjcw";

        CredentialMessage.CredentialContainer container1 = CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType("MembershipCredential")
                .payload(payload1)
                .format("VC1_0_JWT")
                .build();
        CredentialMessage.CredentialContainer container2 = CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType("SensitiveDataCredential")
                .payload(payload2)
                .format("VC1_0_JWT")
                .build();

        CredentialMessage msg = CredentialMessage.Builder.newInstance()
                .issuerPid(issuerPid)
                .holderPid(holderPid)
                .status("ISSUED")
                .rejectionReason(null)
                .requestId(requestId)
                .credentials(List.of(container1, container2))
                .build();

        // Mock credentialRepository.save to return the input VerifiableCredential
        when(credentialRepository.save(any(VerifiableCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // Act
        HolderService.CredentialReceptionResult result = holderService.processIssuedCredentials(msg, issuerPid);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getSavedCount());
        assertEquals(0, result.getSkippedCount());
        assertFalse(result.isEmpty());
        assertFalse(result.hasSkipped());
        // Verify that save was called twice
        verify(credentialRepository, times(2)).save(any(VerifiableCredential.class));
        // Optionally, capture and inspect the saved credentials
        ArgumentCaptor<VerifiableCredential> captor = ArgumentCaptor.forClass(VerifiableCredential.class);
        verify(credentialRepository, times(2)).save(captor.capture());
        List<VerifiableCredential> saved = captor.getAllValues();
        assertEquals("MembershipCredential", saved.get(0).getCredentialType());
        assertEquals("SensitiveDataCredential", saved.get(1).getCredentialType());
        assertEquals(holderPid, saved.get(0).getHolderDid());
        assertEquals(holderPid, saved.get(1).getHolderDid());
        assertEquals(issuerPid, saved.get(0).getIssuerDid());
        assertEquals(issuerPid, saved.get(1).getIssuerDid());
    }
}
