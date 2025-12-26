package it.eng.dcp.issuer.service;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.CredentialRequestMessage;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.issuer.repository.CredentialRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IssuerServiceTest {
    @Mock
    private com.nimbusds.jwt.JWTClaimsSet jwtClaimsSet;
    @Mock
    private it.eng.dcp.common.service.sts.SelfIssuedIdTokenService tokenService;
    @Mock
    private CredentialRequestRepository requestRepository;
    @Mock
    private CredentialDeliveryService deliveryService;
    @Mock
    private CredentialIssuanceService issuanceService;
    @Mock
    private CredentialMetadataService credentialMetadataService;

    @InjectMocks
    private IssuerService issuerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // --- authorizeRequest ---
    @Test
    void authorizeRequest_success() {
        String token = "token";
        when(tokenService.validateToken(token)).thenReturn(jwtClaimsSet);
        JWTClaimsSet result = issuerService.authorizeRequest(token, null);
        assertSame(jwtClaimsSet, result);
    }

    @Test
    void authorizeRequest_nullToken_throws() {
        SecurityException ex = assertThrows(SecurityException.class, () -> issuerService.authorizeRequest(null, null));
        assertTrue(ex.getMessage().contains("Bearer token is required"));
    }

    // --- createCredentialRequest ---
    @Test
    void createCredentialRequest_success() {
        IssuerMetadata metadata = mock(IssuerMetadata.class);
        IssuerMetadata.CredentialObject credObj = mock(IssuerMetadata.CredentialObject.class);
        when(credObj.getCredentialType()).thenReturn("cred1");
        when(metadata.getCredentialsSupported()).thenReturn(List.of(credObj));
        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(metadata);
        CredentialRequestMessage msg = CredentialRequestMessage.Builder.newInstance()
            .holderPid("holder1")
            .credentials(List.of(
                CredentialRequestMessage.CredentialReference.Builder.newInstance().id("cred1").build()
            ))
            .build();
        CredentialRequest req = mock(CredentialRequest.class);
        when(requestRepository.save(any())).thenReturn(req);
        CredentialRequest result = issuerService.createCredentialRequest(msg);
        assertSame(req, result);
    }

    @Test
    void createCredentialRequest_unsupportedCredential_throws() {
        IssuerMetadata metadata = mock(IssuerMetadata.class);
        IssuerMetadata.CredentialObject credObj = mock(IssuerMetadata.CredentialObject.class);
        when(credObj.getCredentialType()).thenReturn("cred1");
        when(metadata.getCredentialsSupported()).thenReturn(List.of(credObj));
        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(metadata);
        CredentialRequestMessage msg = CredentialRequestMessage.Builder.newInstance()
            .holderPid("holder1")
            .credentials(List.of(
                CredentialRequestMessage.CredentialReference.Builder.newInstance().id("bad").build()
            ))
            .build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> issuerService.createCredentialRequest(msg));
        assertTrue(ex.getMessage().contains("Credential type 'bad' is not supported"));
    }

    @Test
    void createCredentialRequest_metadataNotAvailable_throws() {
        CredentialRequestMessage msg = mock(CredentialRequestMessage.class);
        when(credentialMetadataService.buildIssuerMetadata()).thenThrow(new IllegalStateException("no meta"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> issuerService.createCredentialRequest(msg));
        assertTrue(ex.getMessage().contains("Issuer metadata not configured"));
    }

    // --- getRequestByIssuerPid ---
    @Test
    void getRequestByIssuerPid_success() {
        CredentialRequest req = mock(CredentialRequest.class);
        when(requestRepository.findByIssuerPid("id1")).thenReturn(Optional.of(req));
        Optional<CredentialRequest> result = issuerService.getRequestByIssuerPid("id1");
        assertTrue(result.isPresent());
        assertSame(req, result.get());
    }

    @Test
    void getRequestByIssuerPid_nullOrBlank_returnsEmpty() {
        assertTrue(issuerService.getRequestByIssuerPid(null).isEmpty());
        assertTrue(issuerService.getRequestByIssuerPid("").isEmpty());
    }

    // --- approveAndDeliverCredentials ---
    @Test
    void approveAndDeliverCredentials_success_autoGenerate() {
        String requestId = "id1";
        CredentialRequest req = mock(CredentialRequest.class);
        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(req));
        IssuerMetadata metadata = mock(IssuerMetadata.class);
        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(metadata);
        when(req.getCredentialIds()).thenReturn(List.of("cred1"));
        when(issuanceService.generateCredentials(eq(req), any(), any())).thenReturn(List.of(mock(CredentialMessage.CredentialContainer.class)));
        when(deliveryService.deliverCredentials(eq(requestId), any())).thenReturn(true);
        IssuerService.ApprovalResult result = issuerService.approveAndDeliverCredentials(requestId, Map.of(), List.of(), null);
        assertTrue(result.isSuccess());
        assertTrue(result.getCredentialsCount() > 0);
    }

    @Test
    void approveAndDeliverCredentials_success_manualProvision() {
        String requestId = "id1";
        CredentialRequest req = mock(CredentialRequest.class);
        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(req));
        List<Map<String, Object>> provided = List.of(Map.of("credentialType", "cred1", "payload", "data", "format", "jwt"));
        when(deliveryService.deliverCredentials(eq(requestId), any())).thenReturn(true);
        IssuerService.ApprovalResult result = issuerService.approveAndDeliverCredentials(requestId, null, null, provided);
        assertTrue(result.isSuccess());
        assertTrue(result.getCredentialsCount() > 0);
    }

    @Test
    void approveAndDeliverCredentials_requestNotFound_throws() {
        when(requestRepository.findByIssuerPid("bad")).thenReturn(Optional.empty());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> issuerService.approveAndDeliverCredentials("bad", null, null, null));
        assertTrue(ex.getMessage().contains("Credential request not found"));
    }

    @Test
    void approveAndDeliverCredentials_deliveryFails_throws() {
        String requestId = "id1";
        CredentialRequest req = mock(CredentialRequest.class);
        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(req));
        IssuerMetadata metadata = mock(IssuerMetadata.class);
        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(metadata);
        when(req.getCredentialIds()).thenReturn(List.of("cred1"));
        when(issuanceService.generateCredentials(eq(req), any(), any())).thenReturn(List.of(mock(CredentialMessage.CredentialContainer.class)));
        when(deliveryService.deliverCredentials(eq(requestId), any())).thenReturn(false);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> issuerService.approveAndDeliverCredentials(requestId, Map.of(), List.of(), null));
        assertTrue(ex.getMessage().contains("Failed to deliver credentials"));
    }

    // --- rejectCredentialRequest ---
    @Test
    void rejectCredentialRequest_success() {
        String requestId = "id1";
        String reason = "bad reason";
        CredentialRequest req = mock(CredentialRequest.class);
        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(req));
        when(deliveryService.rejectCredentialRequest(requestId, reason)).thenReturn(true);
        assertTrue(issuerService.rejectCredentialRequest(requestId, reason));
    }

    @Test
    void rejectCredentialRequest_missingRequestId_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> issuerService.rejectCredentialRequest(null, "reason"));
        assertTrue(ex.getMessage().contains("requestId is required"));
    }

    @Test
    void rejectCredentialRequest_missingReason_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> issuerService.rejectCredentialRequest("id1", null));
        assertTrue(ex.getMessage().contains("rejectionReason is required"));
    }

    @Test
    void rejectCredentialRequest_requestNotFound_throws() {
        when(requestRepository.findByIssuerPid("bad")).thenReturn(Optional.empty());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> issuerService.rejectCredentialRequest("bad", "reason"));
        assertTrue(ex.getMessage().contains("Credential request not found"));
    }

    // --- getMetadata ---
    @Test
    void getMetadata_success() {
        IssuerMetadata metadata = mock(IssuerMetadata.class);
        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(metadata);
        assertSame(metadata, issuerService.getMetadata());
    }
}
