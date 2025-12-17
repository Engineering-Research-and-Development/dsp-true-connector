package it.eng.dcp.service;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.model.CredentialMessage;
import it.eng.dcp.model.CredentialRequest;
import it.eng.dcp.model.CredentialRequestMessage;
import it.eng.dcp.model.CredentialStatus;
import it.eng.dcp.model.IssuerMetadata;
import it.eng.dcp.repository.CredentialRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock
    private CredentialMetadataService credentialMetadataService;

    private IssuerService issuerService;

    @BeforeEach
    void setUp() {
        issuerService = new IssuerService(tokenService, requestRepository, deliveryService, issuanceService, credentialMetadataService);
    }

    @Test
    void authorizeRequest_validToken_success() {
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
                        .id("MembershipCredential")
                        .build();

        CredentialRequestMessage msg = CredentialRequestMessage.Builder.newInstance()
                .holderPid("did:example:holder123")
                .credentials(List.of(credRef))
                .build();

        IssuerMetadata.CredentialObject supportedCred = IssuerMetadata.CredentialObject.Builder.newInstance()
                .id("test-id")
                .type("CredentialObject")
                .credentialType("MembershipCredential")
                .build();

        IssuerMetadata metadata = IssuerMetadata.Builder.newInstance()
                .issuer("did:web:localhost:8090")
                .credentialsSupported(List.of(supportedCred))
                .build();

        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(metadata);

        CredentialRequest expected = CredentialRequest.Builder.newInstance()
                .issuerPid("req-123")
                .holderPid("did:example:holder123")
                .status(CredentialStatus.RECEIVED)
                .credentialIds(List.of("MembershipCredential"))
                .build();

        when(requestRepository.save(any(CredentialRequest.class))).thenReturn(expected);

        // Act
        CredentialRequest result = issuerService.createCredentialRequest(msg);

        // Assert
        assertNotNull(result);
        assertEquals("req-123", result.getIssuerPid());
        assertEquals("did:example:holder123", result.getHolderPid());
        verify(credentialMetadataService).buildIssuerMetadata();
        verify(requestRepository).save(any(CredentialRequest.class));
    }

    @Test
    void createCredentialRequest_unsupportedCredential_throwsException() {
        // Arrange
        CredentialRequestMessage.CredentialReference credRef =
                CredentialRequestMessage.CredentialReference.Builder.newInstance()
                        .id("UnsupportedCredential")
                        .build();

        CredentialRequestMessage msg = CredentialRequestMessage.Builder.newInstance()
                .holderPid("did:example:holder123")
                .credentials(List.of(credRef))
                .build();

        IssuerMetadata.CredentialObject supportedCred = IssuerMetadata.CredentialObject.Builder.newInstance()
                .id("test-id")
                .type("CredentialObject")
                .credentialType("MembershipCredential")
                .build();

        IssuerMetadata metadata = IssuerMetadata.Builder.newInstance()
                .issuer("did:web:localhost:8090")
                .credentialsSupported(List.of(supportedCred))
                .build();

        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(metadata);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> issuerService.createCredentialRequest(msg));

        assertTrue(exception.getMessage().contains("UnsupportedCredential"));
        assertTrue(exception.getMessage().contains("not supported"));
        verify(credentialMetadataService).buildIssuerMetadata();
        verify(requestRepository, never()).save(any(CredentialRequest.class));
    }

    @Test
    void createCredentialRequest_metadataNotConfigured_throwsException() {
        // Arrange
        CredentialRequestMessage.CredentialReference credRef =
                CredentialRequestMessage.CredentialReference.Builder.newInstance()
                        .id("MembershipCredential")
                        .build();

        CredentialRequestMessage msg = CredentialRequestMessage.Builder.newInstance()
                .holderPid("did:example:holder123")
                .credentials(List.of(credRef))
                .build();

        when(credentialMetadataService.buildIssuerMetadata())
                .thenThrow(new IllegalStateException("No credentials configured"));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> issuerService.createCredentialRequest(msg));

        assertTrue(exception.getMessage().contains("Issuer metadata not configured"));
        verify(credentialMetadataService).buildIssuerMetadata();
        verify(requestRepository, never()).save(any(CredentialRequest.class));
    }

    @Test
    void createCredentialRequest_multipleCredentials_allSupported_success() {
        // Arrange
        CredentialRequestMessage.CredentialReference credRef1 =
                CredentialRequestMessage.CredentialReference.Builder.newInstance()
                        .id("MembershipCredential")
                        .build();

        CredentialRequestMessage.CredentialReference credRef2 =
                CredentialRequestMessage.CredentialReference.Builder.newInstance()
                        .id("CompanyCredential")
                        .build();

        CredentialRequestMessage msg = CredentialRequestMessage.Builder.newInstance()
                .holderPid("did:example:holder123")
                .credentials(List.of(credRef1, credRef2))
                .build();

        IssuerMetadata.CredentialObject supportedCred1 = IssuerMetadata.CredentialObject.Builder.newInstance()
                .id("test-id-1")
                .type("CredentialObject")
                .credentialType("MembershipCredential")
                .build();

        IssuerMetadata.CredentialObject supportedCred2 = IssuerMetadata.CredentialObject.Builder.newInstance()
                .id("test-id-2")
                .type("CredentialObject")
                .credentialType("CompanyCredential")
                .build();

        IssuerMetadata metadata = IssuerMetadata.Builder.newInstance()
                .issuer("did:web:localhost:8090")
                .credentialsSupported(List.of(supportedCred1, supportedCred2))
                .build();

        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(metadata);

        CredentialRequest expected = CredentialRequest.Builder.newInstance()
                .issuerPid("req-123")
                .holderPid("did:example:holder123")
                .status(CredentialStatus.RECEIVED)
                .credentialIds(List.of("MembershipCredential", "CompanyCredential"))
                .build();

        when(requestRepository.save(any(CredentialRequest.class))).thenReturn(expected);

        // Act
        CredentialRequest result = issuerService.createCredentialRequest(msg);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getCredentialIds().size());
        verify(credentialMetadataService).buildIssuerMetadata();
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
        when(issuanceService.generateCredentials(eq(request), isNull(), isNull())).thenReturn(generatedCredentials);
        when(deliveryService.deliverCredentials(eq(requestId), anyList())).thenReturn(true);

        // Act
        IssuerService.ApprovalResult result = issuerService.approveAndDeliverCredentials(requestId, null, null, null);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(2, result.getCredentialsCount());
        assertEquals(2, result.getCredentialTypes().size());
        assertTrue(result.getCredentialTypes().contains("UniversityDegree"));
        assertTrue(result.getCredentialTypes().contains("DriverLicense"));

        verify(requestRepository).findByIssuerPid(requestId);
        verify(issuanceService).generateCredentials(eq(request), isNull(), isNull());
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
        IssuerService.ApprovalResult result = issuerService.approveAndDeliverCredentials(requestId, null, null, providedCredentials);

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
                () -> issuerService.approveAndDeliverCredentials(requestId, null, null, null));

        assertTrue(exception.getMessage().contains("Credential request not found"));
        verify(requestRepository).findByIssuerPid(requestId);
        verify(issuanceService, never()).generateCredentials(any(), any(), any());
        verify(deliveryService, never()).deliverCredentials(anyString(), anyList());
    }

    @Test
    void approveAndDeliverCredentials_nullRequestId_throwsException() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> issuerService.approveAndDeliverCredentials(null, null, null, null));

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
        when(issuanceService.generateCredentials(eq(request), isNull(), isNull())).thenReturn(generatedCredentials);
        when(deliveryService.deliverCredentials(eq(requestId), anyList())).thenReturn(false);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> issuerService.approveAndDeliverCredentials(requestId, null, null, null));

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
        when(issuanceService.generateCredentials(eq(request), isNull(), isNull())).thenReturn(Collections.emptyList());

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> issuerService.approveAndDeliverCredentials(requestId, null, null, null));

        assertEquals("Failed to generate credentials for the requested types", exception.getMessage());
        verify(issuanceService).generateCredentials(eq(request), isNull(), isNull());
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
                () -> issuerService.approveAndDeliverCredentials(requestId, null, null, invalidCredentials));

        assertTrue(exception.getMessage().contains("Each credential must have credentialType, payload, and format"));
        verify(deliveryService, never()).deliverCredentials(anyString(), anyList());
    }

    @Test
    void getMetadata_success() {
        // Arrange
        IssuerMetadata.CredentialObject credentialObject =
                IssuerMetadata.CredentialObject.Builder.newInstance()
                        .id("test-id")
                        .type("CredentialObject")
                        .credentialType("TestCredential")
                        .build();

        IssuerMetadata expectedMetadata = IssuerMetadata.Builder.newInstance()
                .issuer("did:web:localhost:8090")
                .credentialsSupported(List.of(credentialObject))
                .build();

        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(expectedMetadata);

        // Act
        IssuerMetadata result = issuerService.getMetadata();

        // Assert
        assertNotNull(result);
        assertEquals("did:web:localhost:8090", result.getIssuer());
        assertEquals(1, result.getCredentialsSupported().size());
        verify(credentialMetadataService).buildIssuerMetadata();
    }

    @Test
    void authorizeRequest_nullHolderPid_validatesTokenOnly() {
        // Arrange
        String bearerToken = "valid-token";
        String tokenSubject = "did:example:holder123";

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(tokenSubject)
                .build();

        when(tokenService.validateToken(bearerToken)).thenReturn(claims);

        // Act
        JWTClaimsSet result = issuerService.authorizeRequest(bearerToken, null);

        // Assert
        assertNotNull(result);
        assertEquals(tokenSubject, result.getSubject());
        verify(tokenService).validateToken(bearerToken);
    }
}
