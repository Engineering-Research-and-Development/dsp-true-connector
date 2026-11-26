package it.eng.dcp.issuer.service;

import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.model.*;
import it.eng.dcp.common.repository.CredentialRequestRepository;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CredentialDeliveryServiceTest {

    @Mock private CredentialRequestRepository requestRepository;
    @Mock private SelfIssuedIdTokenService tokenService;
    @Mock private SimpleOkHttpRestClient httpClient;
    @Mock private IssuerIdentity issuerIdentity;
    @Mock private DidResolverService didResolverService;
    @Mock private DcpAuditEventPublisher auditPublisher;

    private CredentialDeliveryService deliveryService;

    private CredentialRequest testRequest;
    private List<CredentialMessage.CredentialContainer> testCredentials;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        deliveryService = new CredentialDeliveryService(
                requestRepository, tokenService, issuerIdentity, httpClient, didResolverService, auditPublisher
        );

        testRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123")
                .holderPid("holder-pid-123")
                .holderDid("did:web:example.com:holder")
                .clientAccessToken("holder-access-token")
                .credentialIds(List.of("MembershipCredential"))
                .status(CredentialStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        testCredentials = List.of(
                CredentialMessage.CredentialContainer.Builder.newInstance()
                        .credentialType("MembershipCredential")
                        .format("jwt")
                        .payload("signed-jwt-token")
                        .build()
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) closeable.close();
    }

    @Test
    void deliverCredentials_success() throws Exception {
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(testRequest));
        when(tokenService.createAndSignToken(anyString(), any(), any())).thenReturn("mock-token");
        when(didResolverService.fetchDidDocumentCached("did:web:example.com:holder"))
                .thenReturn(createMockDidDocument("did:web:example.com:holder", "http://example.com/credentials"));
        when(httpClient.executeCall(any(Request.class))).thenReturn(createMockResponse(200, "OK"));

        boolean result = deliveryService.deliverCredentials("issuer-pid-123", testCredentials);

        assertTrue(result);
        verify(tokenService).createAndSignToken("did:web:example.com:holder", "holder-access-token", config);
        ArgumentCaptor<CredentialRequest> savedRequest = ArgumentCaptor.forClass(CredentialRequest.class);
        verify(requestRepository).save(savedRequest.capture());
        assertEquals(CredentialStatus.ISSUED, savedRequest.getValue().getStatus());
        assertEquals("issuer-pid-123", savedRequest.getValue().getIssuerPid());
        assertNull(savedRequest.getValue().getClientAccessToken());

        ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
        verify(auditPublisher).publishEvent(typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any());
        assertEquals(DcpAuditEventType.CREDENTIAL_DELIVERED, typeCaptor.getValue());
    }

    @Test
    void deliverCredentials_httpFailure_returnsFalse() throws Exception {
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(testRequest));
        when(tokenService.createAndSignToken(anyString(), any(), any())).thenReturn("mock-token");
        when(didResolverService.fetchDidDocumentCached("did:web:example.com:holder"))
                .thenReturn(createMockDidDocument("did:web:example.com:holder", "http://example.com/credentials"));
        when(httpClient.executeCall(any(Request.class))).thenReturn(createMockResponse(500, "Internal Server Error"));

        boolean result = deliveryService.deliverCredentials("issuer-pid-123", testCredentials);

        assertFalse(result);
        verify(requestRepository, never()).save(any());

        ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
        verify(auditPublisher).publishEvent(typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any());
        assertEquals(DcpAuditEventType.CREDENTIAL_DELIVERY_FAILED, typeCaptor.getValue());
    }

    @Test
    void rejectCredentialRequest_success() throws Exception {
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(testRequest));
        when(tokenService.createAndSignToken(anyString(), any(), any())).thenReturn("mock-token");
        when(didResolverService.fetchDidDocumentCached("did:web:example.com:holder"))
                .thenReturn(createMockDidDocument("did:web:example.com:holder", "http://example.com/credentials"));
        when(httpClient.executeCall(any(Request.class))).thenReturn(createMockResponse(200, "OK"));

        boolean result = deliveryService.rejectCredentialRequest("issuer-pid-123", "Invalid credentials");

        assertTrue(result);
        verify(tokenService).createAndSignToken("did:web:example.com:holder", "holder-access-token", config);
        ArgumentCaptor<CredentialRequest> savedRequest = ArgumentCaptor.forClass(CredentialRequest.class);
        verify(requestRepository).save(savedRequest.capture());
        assertEquals(CredentialStatus.REJECTED, savedRequest.getValue().getStatus());
        assertEquals("Invalid credentials", savedRequest.getValue().getRejectionReason());
        assertNull(savedRequest.getValue().getClientAccessToken());

        ArgumentCaptor<DcpAuditEventType> typeCaptor = ArgumentCaptor.forClass(DcpAuditEventType.class);
        verify(auditPublisher).publishEvent(typeCaptor.capture(), any(), any(), any(), any(), any(), any(), any());
        assertEquals(DcpAuditEventType.CREDENTIAL_DENIED, typeCaptor.getValue());
    }

    @Test
    @DisplayName("Rejection message carries no placeholder credential and the Holder-side holderPid")
    void rejectCredentialRequest_messageHasNoPlaceholderCredential() throws Exception {
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(testRequest));
        when(tokenService.createAndSignToken(anyString(), any(), any())).thenReturn("mock-token");
        when(didResolverService.fetchDidDocumentCached("did:web:example.com:holder"))
                .thenReturn(createMockDidDocument("did:web:example.com:holder", "http://example.com/credentials"));
        when(httpClient.executeCall(any(Request.class))).thenReturn(createMockResponse(200, "OK"));

        assertTrue(deliveryService.rejectCredentialRequest("issuer-pid-123", "Invalid credentials"));

        ArgumentCaptor<Request> sent = ArgumentCaptor.forClass(Request.class);
        verify(httpClient).executeCall(sent.capture());
        Buffer body = new Buffer();
        sent.getValue().body().writeTo(body);
        JsonNode message = new ObjectMapper().readTree(body.readUtf8());
        assertEquals("REJECTED", message.get("status").asText());
        assertEquals("Invalid credentials", message.get("rejectionReason").asText());
        assertFalse(message.has("credentials"), "a rejection must not carry a placeholder credential");
        assertEquals(testRequest.getHolderPid(), message.get("holderPid").asText());
    }

    @Test
    @DisplayName("Delivery with no credentials sends nothing")
    void deliverCredentials_noCredentials_returnsFalseWithoutSending() throws Exception {
        assertFalse(deliveryService.deliverCredentials("issuer-pid-123", List.of()));
        verify(httpClient, never()).executeCall(any());
    }

    @Test
    @DisplayName("Rejection without reason is sent and audited")
    void rejectCredentialRequest_nullReason_succeeds() throws Exception {
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(testRequest));
        when(tokenService.createAndSignToken(anyString(), any(), any())).thenReturn("mock-token");
        when(didResolverService.fetchDidDocumentCached("did:web:example.com:holder"))
                .thenReturn(createMockDidDocument("did:web:example.com:holder", "http://example.com/credentials"));
        when(httpClient.executeCall(any(Request.class))).thenReturn(createMockResponse(200, "OK"));

        assertTrue(deliveryService.rejectCredentialRequest("issuer-pid-123", null));
        verify(auditPublisher).publishEvent(eq(DcpAuditEventType.CREDENTIAL_DENIED), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deliverCredentials_nullIssuerPid_returnsFalse() {
        assertFalse(deliveryService.deliverCredentials(null, testCredentials));
        verify(requestRepository, never()).findByIssuerPid(any());
    }

    @Test
    void deliverCredentials_blankIssuerPid_returnsFalse() {
        assertFalse(deliveryService.deliverCredentials("", testCredentials));
        verify(requestRepository, never()).findByIssuerPid(any());
    }

    @Test
    void deliverCredentials_requestNotFound_throwsException() {
        when(requestRepository.findByIssuerPid("unknown-pid")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> deliveryService.deliverCredentials("unknown-pid", testCredentials));
    }

    @Test
    void deliverCredentials_alreadyIssued_returnsFalse() {
        CredentialRequest issuedRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123").holderPid("did:web:example.com:holder")
                .credentialIds(List.of("MembershipCredential")).status(CredentialStatus.ISSUED)
                .createdAt(Instant.now()).build();
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(issuedRequest));
        assertFalse(deliveryService.deliverCredentials("issuer-pid-123", testCredentials));
        verify(httpClient, never()).executeCall(any());
    }

    @Test
    void deliverCredentials_alreadyRejected_returnsFalse() {
        CredentialRequest rejectedRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123").holderPid("did:web:example.com:holder")
                .credentialIds(List.of("MembershipCredential")).status(CredentialStatus.REJECTED)
                .createdAt(Instant.now()).build();
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(rejectedRequest));
        assertFalse(deliveryService.deliverCredentials("issuer-pid-123", testCredentials));
        verify(httpClient, never()).executeCall(any());
    }

    @Test
    void deliverCredentials_invalidHolderPid_returnsFalse() {
        CredentialRequest invalidRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123").holderPid("invalid-pid")
                .credentialIds(List.of("MembershipCredential")).status(CredentialStatus.PENDING)
                .createdAt(Instant.now()).build();
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(invalidRequest));
        assertFalse(deliveryService.deliverCredentials("issuer-pid-123", testCredentials));
        verify(httpClient, never()).executeCall(any());
    }

    @Test
    void rejectCredentialRequest_nullIssuerPid_returnsFalse() {
        assertFalse(deliveryService.rejectCredentialRequest(null, "reason"));
        verify(requestRepository, never()).findByIssuerPid(any());
    }

    @Test
    void rejectCredentialRequest_blankIssuerPid_returnsFalse() {
        assertFalse(deliveryService.rejectCredentialRequest("", "reason"));
        verify(requestRepository, never()).findByIssuerPid(any());
    }

    @Test
    void rejectCredentialRequest_requestNotFound_throwsException() {
        when(requestRepository.findByIssuerPid("unknown-pid")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> deliveryService.rejectCredentialRequest("unknown-pid", "reason"));
    }

    @Test
    void rejectCredentialRequest_alreadyIssued_returnsFalse() {
        CredentialRequest issuedRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123").holderPid("did:web:example.com:holder")
                .credentialIds(List.of("MembershipCredential")).status(CredentialStatus.ISSUED)
                .createdAt(Instant.now()).build();
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(issuedRequest));
        assertFalse(deliveryService.rejectCredentialRequest("issuer-pid-123", "reason"));
        verify(httpClient, never()).executeCall(any());
    }

    @Test
    void rejectCredentialRequest_invalidHolderPid_returnsFalse() {
        CredentialRequest invalidRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123").holderPid("invalid-pid")
                .credentialIds(List.of("MembershipCredential")).status(CredentialStatus.PENDING)
                .createdAt(Instant.now()).build();
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(invalidRequest));
        assertFalse(deliveryService.rejectCredentialRequest("issuer-pid-123", "reason"));
        verify(httpClient, never()).executeCall(any());
    }

    @Test
    void rejectCredentialRequest_httpFailure_returnsFalse() throws Exception {
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(testRequest));
        when(tokenService.createAndSignToken(anyString(), any(), any())).thenReturn("mock-token");
        when(didResolverService.fetchDidDocumentCached("did:web:example.com:holder"))
                .thenReturn(createMockDidDocument("did:web:example.com:holder", "http://example.com/credentials"));
        when(httpClient.executeCall(any(Request.class))).thenReturn(createMockResponse(500, "Internal Server Error"));
        assertFalse(deliveryService.rejectCredentialRequest("issuer-pid-123", "reason"));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void deliverCredentials_createsCorrectUrl_withTrailingSlash() throws Exception {
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(testRequest));
        when(tokenService.createAndSignToken(anyString(), any(), any())).thenReturn("mock-token");
        when(didResolverService.fetchDidDocumentCached("did:web:example.com:holder"))
                .thenReturn(createMockDidDocument("did:web:example.com:holder", "http://example.com/"));
        when(httpClient.executeCall(any(Request.class))).thenReturn(createMockResponse(200, "OK"));

        deliveryService.deliverCredentials("issuer-pid-123", testCredentials);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(httpClient).executeCall(requestCaptor.capture());
        assertTrue(requestCaptor.getValue().url().toString().endsWith("/credentials"));
    }

    private Response createMockResponse(int code, String message) {
        return new Response.Builder()
                .request(new Request.Builder().url("http://example.com").build())
                .protocol(Protocol.HTTP_1_1).code(code).message(message)
                .body(ResponseBody.create("{}", MediaType.parse("application/json")))
                .build();
    }

    private DidDocument createMockDidDocument(String holderDid, String credentialServiceUrl) {
        return DidDocument.Builder.newInstance()
                .id(holderDid)
                .service(List.of(new ServiceEntry("credential-service-1", "CredentialService", credentialServiceUrl)))
                .verificationMethod(List.of())
                .build();
    }
}
