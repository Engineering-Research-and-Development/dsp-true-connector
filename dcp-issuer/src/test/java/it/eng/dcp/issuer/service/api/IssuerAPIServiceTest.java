package it.eng.dcp.issuer.service.api;

import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.issuer.repository.CredentialRequestRepository;
import it.eng.dcp.issuer.service.CredentialDeliveryService;
import it.eng.dcp.issuer.service.CredentialIssuanceService;
import it.eng.dcp.issuer.service.CredentialMetadataService;
import it.eng.dcp.issuer.service.IssuerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IssuerAPIServiceTest {

    @InjectMocks
    private IssuerAPIService issuerAPIService;

    @Mock
    private CredentialRequestRepository requestRepository;
    @Mock
    private CredentialDeliveryService deliveryService;
    @Mock
    private CredentialIssuanceService issuanceService;
    @Mock
    private CredentialMetadataService credentialMetadataService;

    // --- approveAndDeliverCredentials ---
    @Test
    void approveAndDeliverCredentials_success_autoGenerate1() {
        String requestId = "id1";
        CredentialRequest req = mock(CredentialRequest.class);
        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(req));
        IssuerMetadata metadata = mock(IssuerMetadata.class);
        when(credentialMetadataService.buildIssuerMetadata()).thenReturn(metadata);
        when(req.getCredentialIds()).thenReturn(List.of("cred1"));
        when(issuanceService.generateCredentials(eq(req), any(), any())).thenReturn(List.of(mock(CredentialMessage.CredentialContainer.class)));
        when(deliveryService.deliverCredentials(eq(requestId), any())).thenReturn(true);
        IssuerService.ApprovalResult result = issuerAPIService.approveAndDeliverCredentials(requestId, Map.of(), List.of(), null);
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
        IssuerService.ApprovalResult result = issuerAPIService.approveAndDeliverCredentials(requestId, null, null, provided);
        assertTrue(result.isSuccess());
        assertTrue(result.getCredentialsCount() > 0);
    }

    @Test
    void approveAndDeliverCredentials_requestNotFound_throws() {
        when(requestRepository.findByIssuerPid("bad")).thenReturn(Optional.empty());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> issuerAPIService.approveAndDeliverCredentials("bad", null, null, null));
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
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> issuerAPIService.approveAndDeliverCredentials(requestId, Map.of(), List.of(), null));
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
        assertTrue(issuerAPIService.rejectCredentialRequest(requestId, reason));
    }

    @Test
    void rejectCredentialRequest_missingRequestId_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> issuerAPIService.rejectCredentialRequest(null, "reason"));
        assertTrue(ex.getMessage().contains("requestId is required"));
    }

    @Test
    void rejectCredentialRequest_missingReason_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> issuerAPIService.rejectCredentialRequest("id1", null));
        assertTrue(ex.getMessage().contains("rejectionReason is required"));
    }

    @Test
    void rejectCredentialRequest_requestNotFound_throws() {
        when(requestRepository.findByIssuerPid("bad")).thenReturn(Optional.empty());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> issuerAPIService.rejectCredentialRequest("bad", "reason"));
        assertTrue(ex.getMessage().contains("Credential request not found"));
    }
}
