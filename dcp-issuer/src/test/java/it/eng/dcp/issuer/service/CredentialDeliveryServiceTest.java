package it.eng.dcp.issuer.service;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.CredentialStatus;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.issuer.client.SimpleOkHttpRestClient;
import it.eng.dcp.issuer.repository.CredentialRequestRepository;
import okhttp3.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Unit tests for CredentialDeliveryService.
 */
class CredentialDeliveryServiceTest {

    @Mock
    private CredentialRequestRepository requestRepository;

    @Mock
    private SelfIssuedIdTokenService tokenService;

    @Mock
    private SimpleOkHttpRestClient httpClient;

    @Mock
    private BaseDidDocumentConfiguration config;

    private CredentialDeliveryService deliveryService;

    private CredentialRequest testRequest;
    private List<CredentialMessage.CredentialContainer> testCredentials;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        // Manually instantiate the service with mocked dependencies
        deliveryService = new CredentialDeliveryService(
                requestRepository,
                tokenService,
                httpClient,
                config,
                false
        );

        testRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123")
                .holderPid("holder-pid-123")
                .holderDid("did:web:example.com:holder")
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
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void deliverCredentials_success() {
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(testRequest));
        when(tokenService.createAndSignToken(anyString(), any(), any())).thenReturn("mock-token");

        Response mockResponse = createMockResponse(200, "OK");
        when(httpClient.executeCall(any(Request.class))).thenReturn(mockResponse);

        boolean result = deliveryService.deliverCredentials("issuer-pid-123", testCredentials);

        assertTrue(result);
        verify(requestRepository).save(argThat(req ->
                req.getStatus() == CredentialStatus.ISSUED &&
                req.getIssuerPid().equals("issuer-pid-123")
        ));
        verify(httpClient).executeCall(any(Request.class));
    }

    @Test
    void deliverCredentials_nullIssuerPid_returnsFalse() {
        boolean result = deliveryService.deliverCredentials(null, testCredentials);
        assertFalse(result);
        verify(requestRepository, never()).findByIssuerPid(any());
    }

    @Test
    void deliverCredentials_blankIssuerPid_returnsFalse() {
        boolean result = deliveryService.deliverCredentials("", testCredentials);
        assertFalse(result);
        verify(requestRepository, never()).findByIssuerPid(any());
    }

    @Test
    void deliverCredentials_requestNotFound_throwsException() {
        when(requestRepository.findByIssuerPid("unknown-pid")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                deliveryService.deliverCredentials("unknown-pid", testCredentials)
        );
    }

    @Test
    void deliverCredentials_alreadyIssued_returnsFalse() {
        CredentialRequest issuedRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123")
                .holderPid("did:web:example.com:holder")
                .credentialIds(List.of("MembershipCredential"))
                .status(CredentialStatus.ISSUED)
                .createdAt(Instant.now())
                .build();

        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(issuedRequest));

        boolean result = deliveryService.deliverCredentials("issuer-pid-123", testCredentials);

        assertFalse(result);
        verify(httpClient, never()).executeCall(any());
    }

    @Test
    void deliverCredentials_alreadyRejected_returnsFalse() {
        CredentialRequest rejectedRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123")
                .holderPid("did:web:example.com:holder")
                .credentialIds(List.of("MembershipCredential"))
                .status(CredentialStatus.REJECTED)
                .createdAt(Instant.now())
                .build();

        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(rejectedRequest));

        boolean result = deliveryService.deliverCredentials("issuer-pid-123", testCredentials);

        assertFalse(result);
        verify(httpClient, never()).executeCall(any());
    }

    @Test
    void deliverCredentials_invalidHolderPid_returnsFalse() {
        CredentialRequest invalidRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123")
                .holderPid("invalid-pid")
                .credentialIds(List.of("MembershipCredential"))
                .status(CredentialStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(invalidRequest));

        boolean result = deliveryService.deliverCredentials("issuer-pid-123", testCredentials);

        assertFalse(result);
        verify(httpClient, never()).executeCall(any());
    }

    @Test
    void deliverCredentials_httpFailure_returnsFalse() {
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(testRequest));
        when(tokenService.createAndSignToken(anyString(), any(), any())).thenReturn("mock-token");

        Response mockResponse = createMockResponse(500, "Internal Server Error");
        when(httpClient.executeCall(any(Request.class))).thenReturn(mockResponse);

        boolean result = deliveryService.deliverCredentials("issuer-pid-123", testCredentials);

        assertFalse(result);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void rejectCredentialRequest_success() {
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(testRequest));
        when(tokenService.createAndSignToken(anyString(), any(), any())).thenReturn("mock-token");

        Response mockResponse = createMockResponse(200, "OK");
        when(httpClient.executeCall(any(Request.class))).thenReturn(mockResponse);

        boolean result = deliveryService.rejectCredentialRequest("issuer-pid-123", "Invalid credentials");

        assertTrue(result);
        verify(requestRepository).save(argThat(req ->
                req.getStatus() == CredentialStatus.REJECTED &&
                req.getRejectionReason().equals("Invalid credentials")
        ));
    }

    @Test
    void rejectCredentialRequest_nullIssuerPid_returnsFalse() {
        boolean result = deliveryService.rejectCredentialRequest(null, "reason");
        assertFalse(result);
        verify(requestRepository, never()).findByIssuerPid(any());
    }

    @Test
    void rejectCredentialRequest_blankIssuerPid_returnsFalse() {
        boolean result = deliveryService.rejectCredentialRequest("", "reason");
        assertFalse(result);
        verify(requestRepository, never()).findByIssuerPid(any());
    }

    @Test
    void rejectCredentialRequest_requestNotFound_throwsException() {
        when(requestRepository.findByIssuerPid("unknown-pid")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                deliveryService.rejectCredentialRequest("unknown-pid", "reason")
        );
    }

    @Test
    void rejectCredentialRequest_alreadyIssued_returnsFalse() {
        CredentialRequest issuedRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123")
                .holderPid("did:web:example.com:holder")
                .credentialIds(List.of("MembershipCredential"))
                .status(CredentialStatus.ISSUED)
                .createdAt(Instant.now())
                .build();

        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(issuedRequest));

        boolean result = deliveryService.rejectCredentialRequest("issuer-pid-123", "reason");

        assertFalse(result);
        verify(httpClient, never()).executeCall(any());
    }

    @Test
    void rejectCredentialRequest_invalidHolderPid_returnsFalse() {
        CredentialRequest invalidRequest = CredentialRequest.Builder.newInstance()
                .issuerPid("issuer-pid-123")
                .holderPid("invalid-pid")
                .credentialIds(List.of("MembershipCredential"))
                .status(CredentialStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(invalidRequest));

        boolean result = deliveryService.rejectCredentialRequest("issuer-pid-123", "reason");

        assertFalse(result);
        verify(httpClient, never()).executeCall(any());
    }

    @Test
    void rejectCredentialRequest_httpFailure_returnsFalse() {
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(testRequest));
        when(tokenService.createAndSignToken(anyString(), any(), any())).thenReturn("mock-token");

        Response mockResponse = createMockResponse(500, "Internal Server Error");
        when(httpClient.executeCall(any(Request.class))).thenReturn(mockResponse);

        boolean result = deliveryService.rejectCredentialRequest("issuer-pid-123", "reason");

        assertFalse(result);
        verify(requestRepository, never()).save(any());
    }

    @Test
    void deliverCredentials_createsCorrectUrl_withTrailingSlash() {
        when(requestRepository.findByIssuerPid("issuer-pid-123")).thenReturn(Optional.of(testRequest));
        when(tokenService.createAndSignToken(anyString(), any(), any())).thenReturn("mock-token");

        Response mockResponse = createMockResponse(200, "OK");
        when(httpClient.executeCall(any(Request.class))).thenReturn(mockResponse);

        deliveryService.deliverCredentials("issuer-pid-123", testCredentials);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(httpClient).executeCall(requestCaptor.capture());

        String url = requestCaptor.getValue().url().toString();
        assertTrue(url.contains("/dcp/credentials"));
    }

    /**
     * Create a mock HTTP response for testing.
     *
     * @param code HTTP status code
     * @param message Response message
     * @return Mock Response object
     */
    private Response createMockResponse(int code, String message) {
        ResponseBody body = ResponseBody.create("{}", MediaType.parse("application/json"));
        return new Response.Builder()
                .request(new Request.Builder().url("http://example.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message)
                .body(body)
                .build();
    }
}

