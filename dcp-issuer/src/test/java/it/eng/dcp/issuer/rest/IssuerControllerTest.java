package it.eng.dcp.issuer.rest;

import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.CredentialRequestMessage;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.issuer.service.IssuerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssuerControllerTest {

    @Mock
    private IssuerService issuerService;
    @Mock
    private JWTClaimsSet jwtClaimsSet;

    @InjectMocks
    private IssuerController issuerController;

    // --- getMetadata tests ---

    @Test
    void getMetadata_success() {
        String token = "valid-token";
        String authHeader = "Bearer " + token;

        IssuerMetadata metadata = mock(IssuerMetadata.class);
        when(issuerService.authorizeRequest(eq(token), isNull())).thenReturn(jwtClaimsSet);
        when(issuerService.getMetadata()).thenReturn(metadata);

        ResponseEntity<?> response = issuerController.getMetadata(authHeader);
        assertEquals(200, response.getStatusCode().value());
        assertSame(metadata, response.getBody());
    }

    @Test
    void getMetadata_missingAuthHeader() {
        ResponseEntity<?> response = issuerController.getMetadata(null);
        assertEquals(401, response.getStatusCode().value());
        assertEquals("Missing or invalid Authorization header", response.getBody());
    }

    @Test
    void getMetadata_invalidAuthHeader() {
        ResponseEntity<?> response = issuerController.getMetadata("Basic abc");
        assertEquals(401, response.getStatusCode().value());
        assertEquals("Missing or invalid Authorization header", response.getBody());
    }

    @Test
    void getMetadata_unauthorized() {
        String token = "bad-token";
        String authHeader = "Bearer " + token;
        doThrow(new SecurityException("bad token")).when(issuerService).authorizeRequest(eq(token), isNull());

        ResponseEntity<?> response = issuerController.getMetadata(authHeader);
        assertEquals(401, response.getStatusCode().value());
        assertEquals("Unauthorized: bad token", response.getBody());
    }

    @Test
    void getMetadata_internalError() {
        String token = "token";
        String authHeader = "Bearer " + token;
        when(issuerService.authorizeRequest(eq(token), isNull())).thenReturn(jwtClaimsSet);

        when(issuerService.getMetadata()).thenThrow(new RuntimeException("fail"));

        ResponseEntity<?> response = issuerController.getMetadata(authHeader);
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("Internal error: fail"));
    }

    // --- createCredentialRequest tests ---

    @Test
    void createCredentialRequest_success() {
        String token = "valid-token";
        String authHeader = "Bearer " + token;
        CredentialRequestMessage msg = mock(CredentialRequestMessage.class);
        when(msg.getHolderPid()).thenReturn("holder1");

        CredentialRequest req = mock(CredentialRequest.class);
        when(req.getIssuerPid()).thenReturn("issuer1");
        when(req.getHolderPid()).thenReturn("holder1");
        when(req.getCredentialIds()).thenReturn(java.util.List.of("cred1"));

        when(issuerService.authorizeRequest(eq(token), eq("holder1"))).thenReturn(jwtClaimsSet);
        when(issuerService.createCredentialRequest(msg)).thenReturn(req);

        ResponseEntity<?> response = issuerController.createCredentialRequest(authHeader, msg);
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getHeaders().getFirst(HttpHeaders.LOCATION));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.LOCATION).contains("/issuer/requests/issuer1"));
    }

    @Test
    void createCredentialRequest_missingAuthHeader() {
        CredentialRequestMessage msg = mock(CredentialRequestMessage.class);
        ResponseEntity<?> response = issuerController.createCredentialRequest(null, msg);
        assertEquals(401, response.getStatusCode().value());
        assertEquals("Missing or invalid Authorization header", response.getBody());
    }

    @Test
    void createCredentialRequest_invalidAuthHeader() {
        CredentialRequestMessage msg = mock(CredentialRequestMessage.class);
        ResponseEntity<?> response = issuerController.createCredentialRequest("Basic abc", msg);
        assertEquals(401, response.getStatusCode().value());
        assertEquals("Missing or invalid Authorization header", response.getBody());
    }

    @Test
    void createCredentialRequest_unauthorized() {
        String token = "bad-token";
        String authHeader = "Bearer " + token;
        CredentialRequestMessage msg = mock(CredentialRequestMessage.class);
        when(msg.getHolderPid()).thenReturn("holder1");
        doThrow(new SecurityException("bad token")).when(issuerService).authorizeRequest(eq(token), eq("holder1"));

        ResponseEntity<?> response = issuerController.createCredentialRequest(authHeader, msg);
        assertEquals(401, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("Unauthorized: bad token"));
    }

    @Test
    void createCredentialRequest_badRequest() {
        String token = "token";
        String authHeader = "Bearer " + token;
        CredentialRequestMessage msg = mock(CredentialRequestMessage.class);
        when(msg.getHolderPid()).thenReturn("holder1");
        when(issuerService.authorizeRequest(eq(token), eq("holder1"))).thenReturn(jwtClaimsSet);
        doThrow(new IllegalArgumentException("bad req")).when(issuerService).createCredentialRequest(msg);

        ResponseEntity<?> response = issuerController.createCredentialRequest(authHeader, msg);
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("bad req"));
    }

    @Test
    void createCredentialRequest_internalError() {
        String token = "token";
        String authHeader = "Bearer " + token;
        CredentialRequestMessage msg = mock(CredentialRequestMessage.class);
        when(msg.getHolderPid()).thenReturn("holder1");
        when(issuerService.authorizeRequest(eq(token), eq("holder1"))).thenReturn(jwtClaimsSet);
        doThrow(new RuntimeException("fail")).when(issuerService).createCredentialRequest(msg);

        ResponseEntity<?> response = issuerController.createCredentialRequest(authHeader, msg);
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("Internal error: fail"));
    }
}
