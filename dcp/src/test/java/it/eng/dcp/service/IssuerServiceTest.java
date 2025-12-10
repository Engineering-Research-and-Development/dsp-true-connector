package it.eng.dcp.service;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.model.CredentialMessage;
import it.eng.dcp.model.CredentialRequest;
import it.eng.dcp.model.CredentialRequestMessage;
import it.eng.dcp.model.CredentialStatus;
import it.eng.dcp.repository.CredentialRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssuerServiceTest {

    @Mock
    private SelfIssuedIdTokenService tokenService;

    @Mock
    private CredentialRequestRepository requestRepository;

    @Mock
    private CredentialDeliveryService deliveryService;

    @Mock
    private CredentialIssuanceService issuanceService;

    private IssuerService issuerService;

    @BeforeEach
    void setUp() {
        issuerService = new IssuerService(tokenService, requestRepository, deliveryService, issuanceService);
    }

    @Test
    void authorizeRequest_validToken_success() throws Exception {
        // Arrange
        String bearerToken = "valid-token";
        String holderPid = "did:example:holder123";

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(holderPid)
                .build();

        when(tokenService.validateToken(bearerToken)).thenReturn(claims);

        // Act
        JWTClaimsSet result = issuerService.authorizeRequest(bearerToken, holderPid);

        // Assert
        assertNotNull(result);
        assertEquals(holderPid, result.getSubject());
        verify(tokenService).validateToken(bearerToken);
    }

    @Test
    void authorizeRequest_nullToken_throwsSecurityException() {
        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class,
                () -> issuerService.authorizeRequest(null, "did:example:holder123"));

        assertEquals("Bearer token is required", exception.getMessage());
        verify(tokenService, never()).validateToken(anyString());
    }

    @Test
    void authorizeRequest_blankToken_throwsSecurityException() {
        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class,
                () -> issuerService.authorizeRequest("   ", "did:example:holder123"));

        assertEquals("Bearer token is required", exception.getMessage());
        verify(tokenService, never()).validateToken(anyString());
    }

    @Test
    void authorizeRequest_mismatchedHolderPid_throwsSecurityException() {
        // Arrange
        String bearerToken = "valid-token";
        String holderPid = "did:example:holder123";
        String differentSubject = "did:example:holder456";

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(differentSubject)
                .build();

        when(tokenService.validateToken(bearerToken)).thenReturn(claims);

        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class,
                () -> issuerService.authorizeRequest(bearerToken, holderPid));

        assertEquals("Token subject does not match holderPid", exception.getMessage());
        verify(tokenService).validateToken(bearerToken);
    }

    @Test
    void createCredentialRequest_validMessage_success() {
        // Arrange
        CredentialRequestMessage.CredentialReference credRef =
                CredentialRequestMessage.CredentialReference.Builder.newInstance()
                        .id("cred-1")
                        .build();

        CredentialRequestMessage msg = CredentialRequestMessage.Builder.newInstance()
                .holderPid("did:example:holder123")
                .credentials(List.of(credRef))
                .build();

        CredentialRequest expected = CredentialRequest.Builder.newInstance()
                .issuerPid("req-123")
                .holderPid("did:example:holder123")
                .status(CredentialStatus.RECEIVED)
                .credentialIds(List.of("cred-1"))
                .build();

        when(requestRepository.save(any(CredentialRequest.class))).thenReturn(expected);

        // Act
        CredentialRequest result = issuerService.createCredentialRequest(msg);

        // Assert
        assertNotNull(result);
        assertEquals("req-123", result.getIssuerPid());
        assertEquals("did:example:holder123", result.getHolderPid());
        verify(requestRepository).save(any(CredentialRequest.class));
    }

    @Test
    void getRequestByIssuerPid_existingRequest_returnsRequest() {
        // Arrange
        String requestId = "req-123";
        CredentialRequest request = CredentialRequest.Builder.newInstance()
                .issuerPid(requestId)
                .holderPid("did:example:holder123")
                .status(CredentialStatus.PENDING)
                .credentialIds(List.of("cred-1"))
                .build();

        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(request));

        // Act
        Optional<CredentialRequest> result = issuerService.getRequestByIssuerPid(requestId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(requestId, result.get().getIssuerPid());
        verify(requestRepository).findByIssuerPid(requestId);
    }

    @Test
    void getRequestByIssuerPid_nonExistingRequest_returnsEmpty() {
        // Arrange
        String requestId = "req-999";
        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.empty());

        // Act
        Optional<CredentialRequest> result = issuerService.getRequestByIssuerPid(requestId);

        // Assert
        assertFalse(result.isPresent());
        verify(requestRepository).findByIssuerPid(requestId);
    }

    @Test
    void getRequestByIssuerPid_nullRequestId_returnsEmpty() {
        // Act
        Optional<CredentialRequest> result = issuerService.getRequestByIssuerPid(null);

        // Assert
        assertFalse(result.isPresent());
        verify(requestRepository, never()).findByIssuerPid(anyString());
    }

    @Test
    void getRequestByIssuerPid_blankRequestId_returnsEmpty() {
        // Act
        Optional<CredentialRequest> result = issuerService.getRequestByIssuerPid("   ");

        // Assert
        assertFalse(result.isPresent());
        verify(requestRepository, never()).findByIssuerPid(anyString());
    }

    @Test
    void approveAndDeliverCredentials_autoGenerate_success() {
        // Arrange
        String requestId = "req-123";
        CredentialRequest request = CredentialRequest.Builder.newInstance()
                .issuerPid(requestId)
                .holderPid("did:example:holder123")
                .status(CredentialStatus.PENDING)
                .credentialIds(List.of("cred-1", "cred-2"))
                .build();

        List<CredentialMessage.CredentialContainer> generatedCredentials = Arrays.asList(
                CredentialMessage.CredentialContainer.Builder.newInstance()
                        .credentialType("UniversityDegree")
                        .payload(Map.of("degree", "BSc"))
                        .format("jwt_vc")
                        .build(),
                CredentialMessage.CredentialContainer.Builder.newInstance()
                        .credentialType("DriverLicense")
                        .payload(Map.of("license", "Class A"))
                        .format("jwt_vc")
                        .build()
        );

        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(request));
        when(issuanceService.generateCredentials(request)).thenReturn(generatedCredentials);
        when(deliveryService.deliverCredentials(eq(requestId), anyList())).thenReturn(true);

        // Act
        IssuerService.ApprovalResult result = issuerService.approveAndDeliverCredentials(requestId, null);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(2, result.getCredentialsCount());
        assertEquals(2, result.getCredentialTypes().size());
        assertTrue(result.getCredentialTypes().contains("UniversityDegree"));
        assertTrue(result.getCredentialTypes().contains("DriverLicense"));

        verify(requestRepository).findByIssuerPid(requestId);
        verify(issuanceService).generateCredentials(request);
        verify(deliveryService).deliverCredentials(eq(requestId), anyList());
    }

    @Test
    void approveAndDeliverCredentials_manualCredentials_success() {
        // Arrange
        String requestId = "req-123";
        CredentialRequest request = CredentialRequest.Builder.newInstance()
                .issuerPid(requestId)
                .holderPid("did:example:holder123")
                .status(CredentialStatus.PENDING)
                .credentialIds(List.of("cred-1"))
                .build();

        List<Map<String, Object>> providedCredentials = Arrays.asList(
                Map.of("credentialType", "CustomCredential",
                       "payload", Map.of("data", "value"),
                       "format", "jwt_vc")
        );

        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(request));
        when(deliveryService.deliverCredentials(eq(requestId), anyList())).thenReturn(true);

        // Act
        IssuerService.ApprovalResult result = issuerService.approveAndDeliverCredentials(requestId, providedCredentials);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(1, result.getCredentialsCount());
        assertEquals(1, result.getCredentialTypes().size());
        assertTrue(result.getCredentialTypes().contains("CustomCredential"));

        verify(requestRepository).findByIssuerPid(requestId);
        verify(issuanceService, never()).generateCredentials(any());
        verify(deliveryService).deliverCredentials(eq(requestId), anyList());
    }

    @Test
    void approveAndDeliverCredentials_requestNotFound_throwsException() {
        // Arrange
        String requestId = "req-999";
        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> issuerService.approveAndDeliverCredentials(requestId, null));

        assertTrue(exception.getMessage().contains("Credential request not found"));
        verify(requestRepository).findByIssuerPid(requestId);
        verify(issuanceService, never()).generateCredentials(any());
        verify(deliveryService, never()).deliverCredentials(anyString(), anyList());
    }

    @Test
    void approveAndDeliverCredentials_nullRequestId_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> issuerService.approveAndDeliverCredentials(null, null));

        assertEquals("requestId is required", exception.getMessage());
        verify(requestRepository, never()).findByIssuerPid(anyString());
    }

    @Test
    void approveAndDeliverCredentials_deliveryFails_throwsException() {
        // Arrange
        String requestId = "req-123";
        CredentialRequest request = CredentialRequest.Builder.newInstance()
                .issuerPid(requestId)
                .holderPid("did:example:holder123")
                .status(CredentialStatus.PENDING)
                .credentialIds(List.of("cred-1"))
                .build();

        List<CredentialMessage.CredentialContainer> generatedCredentials = List.of(
                CredentialMessage.CredentialContainer.Builder.newInstance()
                        .credentialType("TestCredential")
                        .payload(Map.of("test", "data"))
                        .format("jwt_vc")
                        .build()
        );

        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(request));
        when(issuanceService.generateCredentials(request)).thenReturn(generatedCredentials);
        when(deliveryService.deliverCredentials(eq(requestId), anyList())).thenReturn(false);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> issuerService.approveAndDeliverCredentials(requestId, null));

        assertEquals("Failed to deliver credentials to holder", exception.getMessage());
        verify(deliveryService).deliverCredentials(eq(requestId), anyList());
    }

    @Test
    void approveAndDeliverCredentials_emptyGeneratedCredentials_throwsException() {
        // Arrange
        String requestId = "req-123";
        CredentialRequest request = CredentialRequest.Builder.newInstance()
                .issuerPid(requestId)
                .holderPid("did:example:holder123")
                .status(CredentialStatus.PENDING)
                .credentialIds(List.of("cred-1"))
                .build();

        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(request));
        when(issuanceService.generateCredentials(request)).thenReturn(Collections.emptyList());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> issuerService.approveAndDeliverCredentials(requestId, null));

        assertEquals("Failed to generate credentials for the requested types", exception.getMessage());
        verify(issuanceService).generateCredentials(request);
        verify(deliveryService, never()).deliverCredentials(anyString(), anyList());
    }

    @Test
    void rejectCredentialRequest_validRequest_success() {
        // Arrange
        String requestId = "req-123";
        String rejectionReason = "Insufficient documentation";
        CredentialRequest request = CredentialRequest.Builder.newInstance()
                .issuerPid(requestId)
                .holderPid("did:example:holder123")
                .status(CredentialStatus.PENDING)
                .credentialIds(List.of("cred-1"))
                .build();

        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(request));
        when(deliveryService.rejectCredentialRequest(requestId, rejectionReason)).thenReturn(true);

        // Act
        boolean result = issuerService.rejectCredentialRequest(requestId, rejectionReason);

        // Assert
        assertTrue(result);
        verify(requestRepository).findByIssuerPid(requestId);
        verify(deliveryService).rejectCredentialRequest(requestId, rejectionReason);
    }

    @Test
    void rejectCredentialRequest_requestNotFound_throwsException() {
        // Arrange
        String requestId = "req-999";
        String rejectionReason = "Invalid request";
        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> issuerService.rejectCredentialRequest(requestId, rejectionReason));

        assertTrue(exception.getMessage().contains("Credential request not found"));
        verify(requestRepository).findByIssuerPid(requestId);
        verify(deliveryService, never()).rejectCredentialRequest(anyString(), anyString());
    }

    @Test
    void rejectCredentialRequest_nullRequestId_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> issuerService.rejectCredentialRequest(null, "Some reason"));

        assertEquals("requestId is required", exception.getMessage());
        verify(requestRepository, never()).findByIssuerPid(anyString());
    }

    @Test
    void rejectCredentialRequest_blankRequestId_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> issuerService.rejectCredentialRequest("   ", "Some reason"));

        assertEquals("requestId is required", exception.getMessage());
        verify(requestRepository, never()).findByIssuerPid(anyString());
    }

    @Test
    void rejectCredentialRequest_nullRejectionReason_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> issuerService.rejectCredentialRequest("req-123", null));

        assertEquals("rejectionReason is required", exception.getMessage());
        verify(requestRepository, never()).findByIssuerPid(anyString());
    }

    @Test
    void rejectCredentialRequest_blankRejectionReason_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> issuerService.rejectCredentialRequest("req-123", "   "));

        assertEquals("rejectionReason is required", exception.getMessage());
        verify(requestRepository, never()).findByIssuerPid(anyString());
    }

    @Test
    void approveAndDeliverCredentials_invalidManualCredentials_throwsException() {
        // Arrange
        String requestId = "req-123";
        CredentialRequest request = CredentialRequest.Builder.newInstance()
                .issuerPid(requestId)
                .holderPid("did:example:holder123")
                .status(CredentialStatus.PENDING)
                .credentialIds(List.of("cred-1"))
                .build();

        // Missing 'format' field
        List<Map<String, Object>> invalidCredentials = Arrays.asList(
                Map.of("credentialType", "CustomCredential",
                       "payload", Map.of("data", "value"))
        );

        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(request));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> issuerService.approveAndDeliverCredentials(requestId, invalidCredentials));

        assertTrue(exception.getMessage().contains("Each credential must have credentialType, payload, and format"));
        verify(deliveryService, never()).deliverCredentials(anyString(), anyList());
    }
}

