package it.eng.dcp.service;

import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.holder.exception.IssuerServiceNotFoundException;
import it.eng.dcp.holder.service.CredentialIssuanceClient;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CredentialIssuanceClient}.
 *
 * <p>Covers discoverIssuerService, getIssuerMetadata, getPersonalIssuerMetadata,
 * requestCredential, and getRequestStatus.
 */
@ExtendWith(MockitoExtension.class)
class CredentialIssuanceClientTest {

    @Mock
    private SimpleOkHttpRestClient restClient;

    @Mock
    private SelfIssuedIdTokenService tokenService;

    @Mock
    private DidDocumentConfig config;

    @Mock
    private DidResolverService didResolverService;

    @InjectMocks
    private CredentialIssuanceClient client;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(client, "issuerDid", "did:web:issuer.example.com");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private IssuerMetadata createValidIssuerMetadata() {
        var credentialObject = IssuerMetadata.CredentialObject.Builder.newInstance()
                .id("cred-1")
                .credentialType("MembershipCredential")
                .build();
        return IssuerMetadata.Builder.newInstance()
                .issuer("did:web:issuer.example.com")
                .credentialsSupported(List.of(credentialObject))
                .build();
    }

    private DidDocument didDocumentWith(String type, String endpoint) {
        var service = new ServiceEntry("did:web:issuer.example.com#svc", type, endpoint);
        return DidDocument.Builder.newInstance()
                .id("did:web:issuer.example.com")
                .service(List.of(service))
                .build();
    }

    private Response okHttpResponse(String url, int code, String message, String bodyJson) {
        return new Response.Builder()
                .request(new Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message)
                .body(ResponseBody.create(bodyJson, MediaType.parse("application/json")))
                .build();
    }

    // ── discoverIssuerService ─────────────────────────────────────────────────

    @DisplayName("discoverIssuerService returns serviceEndpoint when DID document contains IssuerService entry")
    @Test
    void discoverIssuerService_ReturnsServiceEndpoint_WhenDIDHasIssuerService() throws Exception {
        when(didResolverService.fetchDidDocumentCached(eq("did:web:issuer.example.com")))
                .thenReturn(didDocumentWith("IssuerService", "https://issuer.example.com"));

        assertEquals("https://issuer.example.com", client.discoverIssuerService());
    }

    @DisplayName("discoverIssuerService throws IssuerServiceNotFoundException when DID document is missing IssuerService")
    @Test
    void discoverIssuerService_Throws_WhenDIDMissingService() throws Exception {
        when(didResolverService.fetchDidDocumentCached(eq("did:web:issuer.example.com")))
                .thenReturn(didDocumentWith("OtherService", "https://x"));

        assertThrows(IssuerServiceNotFoundException.class, () -> client.discoverIssuerService());
    }

    @DisplayName("discoverIssuerService throws RuntimeException when DID resolution fails")
    @Test
    void discoverIssuerService_Throws_WhenDIDResolutionFails() throws Exception {
        when(didResolverService.fetchDidDocumentCached(eq("did:web:issuer.example.com")))
                .thenThrow(new IOException("Network error"));

        assertThrows(RuntimeException.class, () -> client.discoverIssuerService());
    }

    // ── getIssuerMetadata ─────────────────────────────────────────────────────

    @DisplayName("getIssuerMetadata returns metadata when token is available")
    @Test
    @SuppressWarnings("unchecked")
    void getIssuerMetadata_ReturnsMetadata_WithToken() throws Exception {
        var metadataUrl = "https://issuer.example.com/metadata";
        var metadata = createValidIssuerMetadata();

        when(tokenService.createAndSignToken(any(), isNull(), any())).thenReturn("valid-token");
        when(restClient.executeAndDeserialize(
                eq(metadataUrl), eq("GET"),
                eq(Map.of("Authorization", "Bearer valid-token")),
                isNull(), eq(IssuerMetadata.class)
        )).thenReturn(metadata);

        var result = client.getIssuerMetadata(metadataUrl);

        assertNotNull(result);
        assertEquals("did:web:issuer.example.com", result.getIssuer());
        assertEquals(1, result.getCredentialsSupported().size());
        verify(tokenService).createAndSignToken(eq("did:web:issuer.example.com"), isNull(), eq(config));
        verify(restClient).executeAndDeserialize(eq(metadataUrl), eq("GET"), any(Map.class), isNull(), eq(IssuerMetadata.class));
    }

    @DisplayName("getIssuerMetadata returns metadata when token creation fails")
    @Test
    @SuppressWarnings("unchecked")
    void getIssuerMetadata_ReturnsMetadata_WithoutToken() throws Exception {
        var metadataUrl = "https://issuer.example.com/metadata";
        var metadata = createValidIssuerMetadata();

        when(tokenService.createAndSignToken(any(), isNull(), any())).thenThrow(new RuntimeException("Token creation failed"));
        when(restClient.executeAndDeserialize(
                eq(metadataUrl), eq("GET"), any(Map.class), isNull(), eq(IssuerMetadata.class)
        )).thenReturn(metadata);

        var result = client.getIssuerMetadata(metadataUrl);

        assertNotNull(result);
        assertEquals("did:web:issuer.example.com", result.getIssuer());
    }

    @DisplayName("getIssuerMetadata throws IllegalArgumentException when metadataUrl is null")
    @Test
    void getIssuerMetadata_Throws_WhenUrlNull() {
        assertThrows(IllegalArgumentException.class, () -> client.getIssuerMetadata(null));
    }

    @DisplayName("getIssuerMetadata throws RuntimeException when HTTP request fails")
    @Test
    void getIssuerMetadata_Throws_WhenHttpFails() throws Exception {
        when(tokenService.createAndSignToken(any(), isNull(), any())).thenReturn("valid-token");
        when(restClient.executeAndDeserialize(any(), any(), any(), any(), any()))
                .thenThrow(new IOException("Network error"));

        var exception = assertThrows(RuntimeException.class,
                () -> client.getIssuerMetadata("https://issuer.example.com/metadata"));
        assertEquals("Failed to fetch issuer metadata from https://issuer.example.com/metadata: Network error",
                exception.getMessage());
    }

    @DisplayName("getIssuerMetadata sends 'Bearer null' header when token is null")
    @Test
    void getIssuerMetadata_SendsBearerNullHeader_WhenTokenNull() throws Exception {
        var metadataUrl = "https://issuer.example.com/metadata";
        var metadata = createValidIssuerMetadata();

        when(tokenService.createAndSignToken(any(), isNull(), any())).thenReturn(null);
        when(restClient.executeAndDeserialize(
                eq(metadataUrl), eq("GET"),
                eq(Map.of("Authorization", "Bearer null")),
                isNull(), eq(IssuerMetadata.class)
        )).thenReturn(metadata);

        assertNotNull(client.getIssuerMetadata(metadataUrl));
        verify(restClient).executeAndDeserialize(
                eq(metadataUrl), eq("GET"),
                eq(Map.of("Authorization", "Bearer null")),
                isNull(), eq(IssuerMetadata.class));
    }

    @DisplayName("getIssuerMetadata sends 'Bearer    ' header when token is blank")
    @Test
    void getIssuerMetadata_SendsBearerBlankHeader_WhenTokenBlank() throws Exception {
        var metadataUrl = "https://issuer.example.com/metadata";
        var metadata = createValidIssuerMetadata();

        when(tokenService.createAndSignToken(any(), isNull(), any())).thenReturn("   ");
        when(restClient.executeAndDeserialize(
                eq(metadataUrl), eq("GET"),
                eq(Map.of("Authorization", "Bearer    ")),
                isNull(), eq(IssuerMetadata.class)
        )).thenReturn(metadata);

        assertNotNull(client.getIssuerMetadata(metadataUrl));
        verify(restClient).executeAndDeserialize(
                eq(metadataUrl), eq("GET"),
                eq(Map.of("Authorization", "Bearer    ")),
                isNull(), eq(IssuerMetadata.class));
    }

    // ── getPersonalIssuerMetadata ─────────────────────────────────────────────

    @DisplayName("getPersonalIssuerMetadata returns metadata from discovered service")
    @Test
    @SuppressWarnings("unchecked")
    void getPersonalIssuerMetadata_ReturnsMetadata_WhenSuccessful() throws Exception {
        when(didResolverService.fetchDidDocumentCached(eq("did:web:issuer.example.com")))
                .thenReturn(didDocumentWith("IssuerService", "https://issuer.example.com"));
        when(tokenService.createAndSignToken(any(), isNull(), any())).thenReturn("valid-token");
        when(restClient.executeAndDeserialize(
                eq("https://issuer.example.com/metadata"), eq("GET"),
                any(Map.class), isNull(), eq(IssuerMetadata.class)
        )).thenReturn(createValidIssuerMetadata());

        var result = client.getPersonalIssuerMetadata();

        assertNotNull(result);
        assertEquals("did:web:issuer.example.com", result.getIssuer());
        verify(didResolverService).fetchDidDocumentCached(eq("did:web:issuer.example.com"));
        verify(restClient).executeAndDeserialize(
                eq("https://issuer.example.com/metadata"), eq("GET"),
                any(Map.class), isNull(), eq(IssuerMetadata.class));
    }

    @DisplayName("getPersonalIssuerMetadata handles service URL with trailing slash")
    @Test
    @SuppressWarnings("unchecked")
    void getPersonalIssuerMetadata_HandlesTrailingSlash() throws Exception {
        when(didResolverService.fetchDidDocumentCached(eq("did:web:issuer.example.com")))
                .thenReturn(didDocumentWith("IssuerService", "https://issuer.example.com/"));
        when(tokenService.createAndSignToken(any(), isNull(), any())).thenReturn("valid-token");
        when(restClient.executeAndDeserialize(
                eq("https://issuer.example.com/metadata"), eq("GET"),
                any(Map.class), isNull(), eq(IssuerMetadata.class)
        )).thenReturn(createValidIssuerMetadata());

        assertNotNull(client.getPersonalIssuerMetadata());
        verify(restClient).executeAndDeserialize(
                eq("https://issuer.example.com/metadata"), eq("GET"),
                any(Map.class), isNull(), eq(IssuerMetadata.class));
    }

    @DisplayName("getPersonalIssuerMetadata throws when DID resolution fails")
    @Test
    void getPersonalIssuerMetadata_Throws_WhenDidResolutionFails() throws Exception {
        when(didResolverService.fetchDidDocumentCached(eq("did:web:issuer.example.com")))
                .thenThrow(new IOException("DID resolution failed"));

        assertThrows(RuntimeException.class, () -> client.getPersonalIssuerMetadata());
    }

    @DisplayName("getPersonalIssuerMetadata throws when metadata fetch fails")
    @Test
    void getPersonalIssuerMetadata_Throws_WhenMetadataFetchFails() throws Exception {
        when(didResolverService.fetchDidDocumentCached(eq("did:web:issuer.example.com")))
                .thenReturn(didDocumentWith("IssuerService", "https://issuer.example.com"));
        when(tokenService.createAndSignToken(any(), isNull(), any())).thenReturn("valid-token");
        when(restClient.executeAndDeserialize(any(), any(), any(), any(), any()))
                .thenThrow(new IOException("Metadata fetch failed"));

        assertThrows(RuntimeException.class, () -> client.getPersonalIssuerMetadata());
    }

    // ── requestCredential ─────────────────────────────────────────────────────

    @DisplayName("requestCredential returns Location header when issuer returns 201 Created")
    @Test
    void requestCredential_ReturnsLocation_On201() throws Exception {
        when(didResolverService.fetchDidDocumentCached(eq("did:web:issuer.example.com")))
                .thenReturn(didDocumentWith("IssuerService", "https://issuer.example.com"));
        when(tokenService.createAndSignToken(any(), any(), any())).thenReturn("mock-token");
        when(restClient.executeCall(any(Request.class))).thenReturn(
                new Response.Builder()
                        .request(new Request.Builder().url("https://issuer.example.com/credentials").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(HttpStatus.CREATED.value())
                        .message("Created")
                        .addHeader(HttpHeaders.LOCATION, "https://issuer.example.com/requests/req123")
                        .body(ResponseBody.create("", MediaType.parse("application/json")))
                        .build());

        assertEquals("https://issuer.example.com/requests/req123",
                client.requestCredential(List.of("cred1")));
    }

    @DisplayName("requestCredential throws RuntimeException on 400 response")
    @Test
    void requestCredential_Throws_On400() throws Exception {
        when(didResolverService.fetchDidDocumentCached(eq("did:web:issuer.example.com")))
                .thenReturn(didDocumentWith("IssuerService", "https://issuer.example.com"));
        when(tokenService.createAndSignToken(any(), any(), any())).thenReturn("mock-token");
        when(restClient.executeCall(any(Request.class))).thenReturn(
                okHttpResponse("https://issuer.example.com/credentials",
                        HttpStatus.BAD_REQUEST.value(), "Bad request", "Bad request"));

        assertThrows(RuntimeException.class, () -> client.requestCredential(List.of("cred1")));
    }

    // ── getRequestStatus ──────────────────────────────────────────────────────

    @DisplayName("getRequestStatus returns 200 response with valid status body")
    @Test
    void getRequestStatus_Returns200_WhenSuccessful() {
        var statusUrl = "https://issuer.example.com/status/req-123";
        var statusJson = "{\"status\":\"completed\",\"credentials\":[]}";

        when(tokenService.createAndSignToken(eq("did:web:issuer.example.com"), isNull(), eq(config)))
                .thenReturn("valid-token");
        when(restClient.executeCall(any(Request.class)))
                .thenReturn(okHttpResponse(statusUrl, HttpStatus.OK.value(), "OK", statusJson));

        var result = client.getRequestStatus(statusUrl, "did:web:issuer.example.com");

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(statusJson, result.getBody());
        verify(tokenService).createAndSignToken(eq("did:web:issuer.example.com"), isNull(), eq(config));
        verify(restClient).executeCall(any(Request.class));
    }

    @DisplayName("getRequestStatus throws IllegalArgumentException when statusUrl is null")
    @Test
    void getRequestStatus_Throws_WhenUrlNull() {
        assertThrows(IllegalArgumentException.class,
                () -> client.getRequestStatus(null, "did:web:issuer.example.com"));
    }

    @DisplayName("getRequestStatus works without Authorization header when token is null")
    @Test
    void getRequestStatus_WorksWithoutToken_WhenTokenNull() {
        var statusUrl = "https://issuer.example.com/status/req-123";

        when(tokenService.createAndSignToken(eq("did:web:issuer.example.com"), isNull(), eq(config))).thenReturn(null);
        when(restClient.executeCall(any(Request.class)))
                .thenReturn(okHttpResponse(statusUrl, HttpStatus.OK.value(), "OK", "{\"status\":\"pending\"}"));

        var result = client.getRequestStatus(statusUrl, "did:web:issuer.example.com");

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @DisplayName("getRequestStatus works without Authorization header when token is blank")
    @Test
    void getRequestStatus_WorksWithoutToken_WhenTokenBlank() {
        var statusUrl = "https://issuer.example.com/status/req-123";

        when(tokenService.createAndSignToken(eq("did:web:issuer.example.com"), isNull(), eq(config))).thenReturn("   ");
        when(restClient.executeCall(any(Request.class)))
                .thenReturn(okHttpResponse(statusUrl, HttpStatus.OK.value(), "OK", "{\"status\":\"processing\"}"));

        var result = client.getRequestStatus(statusUrl, "did:web:issuer.example.com");

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @DisplayName("getRequestStatus returns 202 Accepted")
    @Test
    void getRequestStatus_Returns202_WhenAccepted() {
        var statusUrl = "https://issuer.example.com/status/req-123";

        when(tokenService.createAndSignToken(eq("did:web:issuer.example.com"), isNull(), eq(config))).thenReturn("valid-token");
        when(restClient.executeCall(any(Request.class)))
                .thenReturn(okHttpResponse(statusUrl, HttpStatus.ACCEPTED.value(), "Accepted", "{\"status\":\"processing\"}"));

        var result = client.getRequestStatus(statusUrl, "did:web:issuer.example.com");

        assertNotNull(result);
        assertEquals(HttpStatus.ACCEPTED, result.getStatusCode());
    }

    @DisplayName("getRequestStatus returns 404 Not Found")
    @Test
    void getRequestStatus_Returns404_WhenNotFound() {
        var statusUrl = "https://issuer.example.com/status/req-123";

        when(tokenService.createAndSignToken(eq("did:web:issuer.example.com"), isNull(), eq(config))).thenReturn("valid-token");
        when(restClient.executeCall(any(Request.class)))
                .thenReturn(okHttpResponse(statusUrl, HttpStatus.NOT_FOUND.value(), "Not Found", "{\"error\":\"Request not found\"}"));

        var result = client.getRequestStatus(statusUrl, "did:web:issuer.example.com");

        assertNotNull(result);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @DisplayName("getRequestStatus throws RuntimeException when response is null")
    @Test
    void getRequestStatus_Throws_WhenResponseNull() {
        var statusUrl = "https://issuer.example.com/status/req-123";

        when(tokenService.createAndSignToken(eq("did:web:issuer.example.com"), isNull(), eq(config))).thenReturn("valid-token");
        when(restClient.executeCall(any(Request.class))).thenReturn(null);

        var exception = assertThrows(RuntimeException.class,
                () -> client.getRequestStatus(statusUrl, "did:web:issuer.example.com"));
        assertEquals("No response from issuer at " + statusUrl, exception.getMessage());
    }

    @DisplayName("getRequestStatus preserves response headers")
    @Test
    void getRequestStatus_PreservesHeaders() {
        var statusUrl = "https://issuer.example.com/status/req-123";

        when(tokenService.createAndSignToken(eq("did:web:issuer.example.com"), isNull(), eq(config))).thenReturn("valid-token");
        when(restClient.executeCall(any(Request.class))).thenReturn(
                new Response.Builder()
                        .request(new Request.Builder().url(statusUrl).build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(HttpStatus.OK.value())
                        .message("OK")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("X-Request-ID", "req-123")
                        .body(ResponseBody.create("{\"status\":\"completed\"}", MediaType.parse("application/json")))
                        .build());

        var result = client.getRequestStatus(statusUrl, "did:web:issuer.example.com");

        assertNotNull(result.getHeaders());
        assertEquals("application/json", result.getHeaders().getFirst("Content-Type"));
        assertEquals("req-123", result.getHeaders().getFirst("X-Request-ID"));
    }

    @DisplayName("getRequestStatus handles empty response body")
    @Test
    void getRequestStatus_HandlesEmptyBody() {
        var statusUrl = "https://issuer.example.com/status/req-123";

        when(tokenService.createAndSignToken(eq("did:web:issuer.example.com"), isNull(), eq(config))).thenReturn("valid-token");
        when(restClient.executeCall(any(Request.class))).thenReturn(
                new Response.Builder()
                        .request(new Request.Builder().url(statusUrl).build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(HttpStatus.OK.value())
                        .message("OK")
                        .body(ResponseBody.create("", MediaType.parse("application/json")))
                        .build());

        var result = client.getRequestStatus(statusUrl, "did:web:issuer.example.com");

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @DisplayName("getRequestStatus resolves unknown status code to 500")
    @Test
    void getRequestStatus_ResolvesUnknownStatusTo500() {
        var statusUrl = "https://issuer.example.com/status/req-123";

        when(tokenService.createAndSignToken(eq("did:web:issuer.example.com"), isNull(), eq(config))).thenReturn("valid-token");
        when(restClient.executeCall(any(Request.class))).thenReturn(
                new Response.Builder()
                        .request(new Request.Builder().url(statusUrl).build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(999)
                        .message("Unknown")
                        .body(ResponseBody.create("{}", MediaType.parse("application/json")))
                        .build());

        var result = client.getRequestStatus(statusUrl, "did:web:issuer.example.com");

        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
    }
}


