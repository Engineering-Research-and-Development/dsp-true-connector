package it.eng.dcp.issuer.service.api;

import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.repository.CredentialRequestRepository;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.issuer.service.CredentialDeliveryService;
import it.eng.dcp.issuer.service.CredentialIssuanceException;
import it.eng.dcp.issuer.service.CredentialIssuanceService;
import it.eng.dcp.issuer.service.IssuerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IssuerAPIServiceTest {

    @InjectMocks
    private IssuerAPIService issuerAPIService;

    @Mock private CredentialRequestRepository requestRepository;
    @Mock private CredentialDeliveryService deliveryService;
    @Mock private CredentialIssuanceService issuanceService;
    @Mock private DcpAuditEventPublisher auditPublisher;

    @Test
    void approveAndDeliverCredentials_success_autoGenerate1() {
        String requestId = "id1";
        CredentialRequest req = mock(CredentialRequest.class);
        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(req));
        when(req.getCredentialIds()).thenReturn(List.of("cred1"));
        CredentialMessage.CredentialContainer container = mock(CredentialMessage.CredentialContainer.class);
        when(container.getCredentialType()).thenReturn("MembershipCredential");
        when(issuanceService.generateCredentials(eq(req), any(), any())).thenReturn(List.of(container));
        when(deliveryService.deliverCredentials(eq(requestId), any())).thenReturn(true);

        IssuerService.ApprovalResult result = issuerAPIService.approveAndDeliverCredentials(requestId, Map.of(), List.of(), null);

        assertTrue(result.isSuccess());
        assertTrue(result.getCredentialsCount() > 0);

        ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
        verify(auditPublisher).publishEvent(typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any());
        assertEquals(DcpAuditEventType.CREDENTIAL_APPROVED, typeCaptor.getValue());
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
        when(req.getCredentialIds()).thenReturn(List.of("cred1"));
        when(issuanceService.generateCredentials(eq(req), any(), any())).thenReturn(List.of(mock(CredentialMessage.CredentialContainer.class)));
        when(deliveryService.deliverCredentials(eq(requestId), any())).thenReturn(false);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> issuerAPIService.approveAndDeliverCredentials(requestId, Map.of(), List.of(), null));
        assertTrue(ex.getMessage().contains("Failed to deliver credentials"));
    }

    @Test
    void approveAndDeliverCredentials_partialIssuanceFailure_sendsRejectedAndDeliversNothing() {
        String requestId = "id1";
        CredentialRequest req = mock(CredentialRequest.class);
        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(req));
        when(req.getCredentialIds()).thenReturn(List.of("cred1", "cred2"));
        when(issuanceService.generateCredentials(eq(req), any(), any()))
                .thenThrow(new CredentialIssuanceException("Failed to issue credential(s) [cred2]", List.of("cred2")));
        when(deliveryService.rejectCredentialRequest(eq(requestId), contains("cred2"))).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> issuerAPIService.approveAndDeliverCredentials(requestId, Map.of(), List.of(), null));

        assertTrue(ex.getMessage().contains("request rejected"));
        verify(deliveryService, never()).deliverCredentials(any(), any());
        verify(auditPublisher, never()).publishEvent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void approveAndDeliverCredentials_partialIssuanceFailure_rejectionNotDelivered_reportsIt() {
        String requestId = "id1";
        CredentialRequest req = mock(CredentialRequest.class);
        when(requestRepository.findByIssuerPid(requestId)).thenReturn(Optional.of(req));
        when(issuanceService.generateCredentials(eq(req), any(), any()))
                .thenThrow(new CredentialIssuanceException("Failed to issue credential(s) [cred2]", List.of("cred2")));
        when(deliveryService.rejectCredentialRequest(eq(requestId), any())).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> issuerAPIService.approveAndDeliverCredentials(requestId, Map.of(), List.of(), null));

        assertTrue(ex.getMessage().contains("rejection could not be delivered"));
        verify(deliveryService, never()).deliverCredentials(any(), any());
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
