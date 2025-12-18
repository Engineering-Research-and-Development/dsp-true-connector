package it.eng.dcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.CredentialStatus;
import it.eng.dcp.core.ProfileResolver;
import it.eng.dcp.model.*;
import it.eng.dcp.repository.CredentialStatusRepository;
import it.eng.dcp.repository.VerifiableCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
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
}

