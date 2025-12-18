package it.eng.dcp.rest;

import it.eng.dcp.common.model.CredentialStatus;
import it.eng.dcp.model.CredentialRequest;
import it.eng.dcp.service.IssuerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IssuerControllerTest {

    @Mock
    private IssuerService issuerService;

    @InjectMocks
    private IssuerController controller;

    @Test
    public void getRequestStatus_found() {
        CredentialRequest req = CredentialRequest.Builder.newInstance()
                .issuerPid("req-123")
                .holderPid("did:example:holder1")
                .status(CredentialStatus.PENDING)
                .createdAt(Instant.parse("2025-11-03T10:00:00Z"))
                .credentialIds(java.util.List.of("d5c77b0e-7f4e-4fd5-8c5f-28b5fc3f96d1"))
                .build();

        when(issuerService.getRequestByIssuerPid("req-123")).thenReturn(Optional.of(req));

        var resp = controller.getRequestStatus("req-123");
        assertEquals(HttpStatusCode.valueOf(200), resp.getStatusCode());
        assertInstanceOf(Map.class, resp.getBody());
        Map<?, ?> body = (Map<?, ?>) resp.getBody();
        assertEquals("req-123", body.get("issuerPid"));
        assertEquals("did:example:holder1", body.get("holderPid"));
        assertEquals("PENDING", body.get("status"));
    }

    @Test
    public void getRequestStatus_notFound() {
        when(issuerService.getRequestByIssuerPid("req-404")).thenReturn(Optional.empty());

        var resp = controller.getRequestStatus("req-404");
        assertEquals(HttpStatusCode.valueOf(404), resp.getStatusCode());
    }
}

