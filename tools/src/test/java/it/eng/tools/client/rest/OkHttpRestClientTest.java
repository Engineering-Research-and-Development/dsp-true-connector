package it.eng.tools.client.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import it.eng.tools.auth.M2mTokenUnavailableException;
import it.eng.tools.auth.internal.InternalCallClaims;
import it.eng.tools.auth.internal.InternalCallTokenIssuer;
import it.eng.tools.model.ExternalData;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.TenantContextHolder;
import it.eng.tools.util.CredentialUtils;
import okhttp3.*;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OkHttpRestClientTest {

    private static final String BASIC_AUTH = "basicAuth";
    private static final String TARGET_ADDRESS = "http://test.endpoint/123";
    private static final String ATTACHMENT_FILENAME = "attachment;filename=";

    @Mock
    private OkHttpClient okHttpClient;
    @Mock
    private CredentialUtils credentialUtils;
    @Mock
    private RequestBody formBody;
    @Mock
    private Request request;
    @Mock
    private Call call;
    @Mock
    private Response response;
    @Mock
    private ResponseBody responseBody;

    private final InternalCallTokenIssuer internalCallTokenIssuer = new InternalCallTokenIssuer();

    private OkHttpRestClient okHttpRestClient;

    @BeforeEach
    public void setup() {
        okHttpRestClient = new OkHttpRestClient(okHttpClient, credentialUtils, internalCallTokenIssuer, "123", false);
    }

    @AfterEach
    public void clearTenantContext() {
        TenantContextHolder.clear();
    }

    private Optional<InternalCallClaims> internalClaims(Request request) {
        String authorization = request.header(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return internalCallTokenIssuer.verify(authorization.substring("Bearer ".length()));
    }

    private static String bodyText(Request request) {
        try (Buffer buffer = new Buffer()) {
            if (request.body() == null) {
                return "";
            }
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Send protocol request - success")
    public void callSuccessful() throws IOException {
        when(okHttpClient.newCall(request)).thenReturn(call);
        when(call.execute()).thenReturn(response);
        okHttpRestClient.executeCall(request);
    }

    @Test
    @DisplayName("Send protocol request - error")
    public void callError() throws IOException {
        when(okHttpClient.newCall(request)).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("Error"));

        assertNull(okHttpRestClient.executeCall(request));
    }

    @Test
    @DisplayName("Send protocol request - success")
    public void sendProtocolRequest_success() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String newString = "{\"test\": \"example\"}";
        JsonNode jsonNode = mapper.readTree(newString);

        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);

        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("This is answer from test");
        when(response.isSuccessful()).thenReturn(true);

        GenericApiResponse<String> apiResponse = okHttpRestClient.sendRequestProtocol(TARGET_ADDRESS, jsonNode, BASIC_AUTH);

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
    }

    @Test
    @DisplayName("Send protocol request - error")
    public void sendProtocolRequest_error() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String newString = "{\"test\": \"example_error\"}";
        JsonNode jsonNode = mapper.readTree(newString);

        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);

        when(response.code()).thenReturn(400);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("This is ERROR answer from test");
        when(response.isSuccessful()).thenReturn(false);

        GenericApiResponse<String> apiResponse = okHttpRestClient.sendRequestProtocol(TARGET_ADDRESS, jsonNode, BASIC_AUTH);

        assertNotNull(apiResponse);
        assertFalse(apiResponse.isSuccess());
    }

    @Test
    @DisplayName("Send protocol request - null body")
    public void sendProtocolRequest_nullBody() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Success");
        when(response.isSuccessful()).thenReturn(true);

        GenericApiResponse<String> apiResponse = okHttpRestClient.sendRequestProtocol(TARGET_ADDRESS, null, BASIC_AUTH);

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        verify(okHttpClient).newCall(any(Request.class));
    }

    @Test
    @DisplayName("Send protocol request - IOException")
    public void sendProtocolRequest_IOException() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree("{\"test\": \"example\"}");

        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("Network error"));

        GenericApiResponse<String> apiResponse = okHttpRestClient.sendRequestProtocol(TARGET_ADDRESS, jsonNode, BASIC_AUTH);

        assertNotNull(apiResponse);
        assertFalse(apiResponse.isSuccess());
        assertEquals("Network error", apiResponse.getMessage());
    }

    @Test
    @DisplayName("Send GET request - success")
    public void sendGETRequest_success() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);

        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("This is answer from test");
        when(response.isSuccessful()).thenReturn(true);

        GenericApiResponse<String> apiResponse = okHttpRestClient.sendGETRequest(TARGET_ADDRESS, BASIC_AUTH);

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
    }

    @Test
    @DisplayName("Send GET request - error")
    public void sendGETRequest_error() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);

        when(response.code()).thenReturn(400);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("This is ERROR answer from test");
        when(response.isSuccessful()).thenReturn(false);

        GenericApiResponse<String> apiResponse = okHttpRestClient.sendGETRequest(TARGET_ADDRESS, BASIC_AUTH);

        assertNotNull(apiResponse);
        assertFalse(apiResponse.isSuccess());
    }

    @Test
    @DisplayName("Send GET request - IOException")
    public void sendGETRequest_IOException() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("Network error"));

        GenericApiResponse<String> apiResponse = okHttpRestClient.sendGETRequest(TARGET_ADDRESS, BASIC_AUTH);

        assertNotNull(apiResponse);
        assertFalse(apiResponse.isSuccess());
        assertEquals("Network error", apiResponse.getMessage());
    }

    @Test
    @DisplayName("Download data - success")
    public void downloadData_success() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);

        byte[] bodyBytes = "This is answer from test".getBytes();
        InputStream stream = new ByteArrayInputStream(bodyBytes);
        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
//        when(responseBody.byteStream()).thenReturn(stream);
        when(response.body().contentType()).thenReturn(MediaType.get("text/plain"));
        when(response.body().bytes()).thenReturn(bodyBytes);
        when(response.isSuccessful()).thenReturn(true);

        GenericApiResponse<ExternalData> apiResponse = okHttpRestClient.downloadData(TARGET_ADDRESS, BASIC_AUTH);

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertEquals(new String(bodyBytes), new String(apiResponse.getData().getData()));
        assertEquals(MediaType.get("text/plain"), apiResponse.getData().getContentType());
    }

    @Test
    @DisplayName("Download data - error")
    public void downloadData_error() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);

        when(response.code()).thenReturn(400);
        when(response.isSuccessful()).thenReturn(false);

        GenericApiResponse<ExternalData> apiResponse = okHttpRestClient.downloadData(TARGET_ADDRESS, BASIC_AUTH);

        assertNotNull(apiResponse);
        assertFalse(apiResponse.isSuccess());
    }

    @Test
    @DisplayName("Download data - null response body")
    public void downloadData_nullResponseBody() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);

        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(null);
        when(response.isSuccessful()).thenReturn(true);

        GenericApiResponse<ExternalData> apiResponse = okHttpRestClient.downloadData(TARGET_ADDRESS, BASIC_AUTH);

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertNull(apiResponse.getData().getData());
    }

    @Test
    @DisplayName("Download data - null content disposition")
    public void downloadData_nullContentDisposition() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);

        byte[] bodyBytes = "This is answer from test".getBytes();
        InputStream stream = new ByteArrayInputStream(bodyBytes);
        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
//        when(responseBody.byteStream()).thenReturn(stream);
        when(response.body().bytes()).thenReturn(bodyBytes);
        when(response.body().contentType()).thenReturn(MediaType.get("text/plain"));
        when(response.isSuccessful()).thenReturn(true);
        when(response.header(HttpHeaders.CONTENT_DISPOSITION)).thenReturn(null);

        GenericApiResponse<ExternalData> apiResponse = okHttpRestClient.downloadData(TARGET_ADDRESS, BASIC_AUTH);

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertEquals(ATTACHMENT_FILENAME + TARGET_ADDRESS.substring(TARGET_ADDRESS.lastIndexOf("/") + 1), apiResponse.getData().getContentDisposition());
        assertEquals(new String(bodyBytes), new String(apiResponse.getData().getData()));
        assertEquals(MediaType.get("text/plain"), apiResponse.getData().getContentType());
    }

    @Test
    @DisplayName("Download data - IOException")
    public void downloadData_IOException() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("Network error"));

        GenericApiResponse<ExternalData> apiResponse = okHttpRestClient.downloadData(TARGET_ADDRESS, BASIC_AUTH);

        assertNotNull(apiResponse);
        assertFalse(apiResponse.isSuccess());
        assertEquals("Network error", apiResponse.getMessage());
    }

    @Test
    @DisplayName("Send internal request - GET carries a fresh internal-call token for the current tenant")
    public void sendInternalRequest_GET() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Success");
        TenantContextHolder.setTenantId("tenant-1");

        String result = okHttpRestClient.sendInternalRequest("/internal/test", HttpMethod.GET, null);

        assertNotNull(result);
        assertEquals("Success", result);
        verify(okHttpClient).newCall(argThat(request ->
                request.method().equals("GET")
                        && request.url().toString().equals("http://localhost:123/internal/test")
                        && request.header(TenantContextHolder.HEADER_X_TENANT_ID) == null
                        && internalClaims(request)
                        .filter(InternalCallClaims::hasInternalRole)
                        .flatMap(InternalCallClaims::tenantId)
                        .filter("tenant-1"::equals)
                        .isPresent()));
        Mockito.verifyNoInteractions(credentialUtils);
    }

    @Test
    @DisplayName("Send internal request - POST without tenant context carries a token without tenant")
    public void sendInternalRequest_POST() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree("{\"test\": \"example\"}");

        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Success");

        String result = okHttpRestClient.sendInternalRequest("/internal/test", HttpMethod.POST, jsonNode);

        assertNotNull(result);
        assertEquals("Success", result);
        verify(okHttpClient).newCall(argThat(request ->
                request.method().equals("POST")
                        && request.header(TenantContextHolder.HEADER_X_TENANT_ID) == null
                        && internalClaims(request)
                        .filter(InternalCallClaims::hasInternalRole)
                        .filter(claims -> claims.tenantId().isEmpty())
                        .isPresent()));
    }

    @Test
    @DisplayName("Send internal request - SSL enabled")
    public void sendInternalRequest_SSL() throws IOException {
        OkHttpRestClient sslClient = new OkHttpRestClient(okHttpClient, credentialUtils, internalCallTokenIssuer,
                "8443", true);

        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Success");

        String result = sslClient.sendInternalRequest("/internal/test", HttpMethod.GET, null);

        assertNotNull(result);
        verify(okHttpClient).newCall(argThat(request ->
                request.url().toString().startsWith("https://localhost:8443")));
    }

    @Test
    @DisplayName("Send internal request - IOException")
    public void sendInternalRequest_IOException() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("Network error"));

        String result = okHttpRestClient.sendInternalRequest("/internal/test", HttpMethod.GET, null);

        assertNull(result);
    }

    @Test
    @DisplayName("Send internal request - a 401 is returned as-is without a retry or credential eviction")
    public void sendInternalRequest_401_noRetry() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.code()).thenReturn(401);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Unauthorized");

        String result = okHttpRestClient.sendInternalRequest("/internal/test", HttpMethod.GET, null);

        assertEquals("Unauthorized", result);
        verify(okHttpClient, Mockito.times(1)).newCall(any(Request.class));
        Mockito.verifyNoInteractions(credentialUtils);
    }

    @Test
    @DisplayName("Send internal request - every call mints a new token")
    public void sendInternalRequest_mintsNewTokenPerCall() throws IOException {
        List<String> authorizations = new ArrayList<>();
        when(okHttpClient.newCall(any(Request.class))).thenAnswer(invocation -> {
            authorizations.add(invocation.<Request>getArgument(0).header(HttpHeaders.AUTHORIZATION));
            return call;
        });
        when(call.execute()).thenReturn(response);
        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Success");

        okHttpRestClient.sendInternalRequest("/internal/test", HttpMethod.GET, null);
        TenantContextHolder.setTenantId("tenant-2");
        okHttpRestClient.sendInternalRequest("/internal/test", HttpMethod.GET, null);

        assertEquals(2, authorizations.size());
        assertNotEquals(authorizations.get(0), authorizations.get(1));
    }

    @Test
    @DisplayName("Send internal POST - carries the body and a fresh internal-call token for the current tenant")
    public void sendInternalPostRequest_success() throws IOException {
        JsonNode jsonNode = new ObjectMapper().readTree("{\"test\": \"example\"}");
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Success");
        when(response.isSuccessful()).thenReturn(true);
        TenantContextHolder.setTenantId("tenant-1");

        GenericApiResponse<String> result = okHttpRestClient.sendInternalPostRequest("/internal/test", jsonNode);

        assertTrue(result.isSuccess());
        assertEquals("Success", result.getData());
        verify(okHttpClient).newCall(argThat(request ->
                request.method().equals("POST")
                        && request.url().toString().equals("http://localhost:123/internal/test")
                        && bodyText(request).contains("example")
                        && request.header(TenantContextHolder.HEADER_X_TENANT_ID) == null
                        && internalClaims(request)
                        .filter(InternalCallClaims::hasInternalRole)
                        .flatMap(InternalCallClaims::tenantId)
                        .filter("tenant-1"::equals)
                        .isPresent()));
        Mockito.verifyNoInteractions(credentialUtils);
    }

    @Test
    @DisplayName("Send internal POST - a 401 is a failure without a retry or M2M credential eviction")
    public void sendInternalPostRequest_401_noRetryNoEviction() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.code()).thenReturn(401);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Unauthorized");
        when(response.isSuccessful()).thenReturn(false);

        GenericApiResponse<String> result = okHttpRestClient.sendInternalPostRequest("/internal/test", null);

        assertFalse(result.isSuccess());
        verify(okHttpClient, Mockito.times(1)).newCall(any(Request.class));
        Mockito.verifyNoInteractions(credentialUtils);
    }

    @Test
    @DisplayName("Send internal POST - IOException is a failure")
    public void sendInternalPostRequest_IOException() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenThrow(new IOException("Network error"));

        GenericApiResponse<String> result = okHttpRestClient.sendInternalPostRequest("/internal/test", null);

        assertFalse(result.isSuccess());
        assertEquals("Network error", result.getMessage());
    }

    @Test
    @DisplayName("Send protocol request with Supplier - retries once on 401 and succeeds")
    public void sendRequestProtocolSupplier_retriesOnceOn401() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree("{\"test\": \"example\"}");

        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.code()).thenReturn(401, 200);
        when(response.isSuccessful()).thenReturn(false, true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Unauthorized", "Success");

        AtomicInteger callCount = new AtomicInteger();
        Supplier<String> authorizationSupplier = () ->
                callCount.incrementAndGet() == 1 ? "Bearer stale-token" : "Bearer fresh-token";

        GenericApiResponse<String> apiResponse = okHttpRestClient.sendRequestProtocol(TARGET_ADDRESS, jsonNode, authorizationSupplier);

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertEquals(2, callCount.get());
        verify(credentialUtils).invalidateCachedCredentials();
        verify(okHttpClient, Mockito.times(2)).newCall(any(Request.class));
    }

    @Test
    @DisplayName("Send protocol request with Supplier - success on first attempt never invalidates credentials")
    public void sendRequestProtocolSupplier_successFirstTry_noInvalidate() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree("{\"test\": \"example\"}");

        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.code()).thenReturn(200);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Success");

        AtomicInteger callCount = new AtomicInteger();
        Supplier<String> authorizationSupplier = () -> {
            callCount.incrementAndGet();
            return "Bearer valid-token";
        };

        GenericApiResponse<String> apiResponse = okHttpRestClient.sendRequestProtocol(TARGET_ADDRESS, jsonNode, authorizationSupplier);

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        assertEquals(1, callCount.get());
        verify(credentialUtils, Mockito.never()).invalidateCachedCredentials();
        verify(okHttpClient, Mockito.times(1)).newCall(any(Request.class));
    }

    @Test
    @DisplayName("Send protocol request with Supplier and tenantId - propagates tenant header and retries on 401")
    public void sendRequestProtocolSupplierWithTenant_retriesOnceOn401() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree("{\"test\": \"example\"}");

        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.code()).thenReturn(401, 200);
        when(response.isSuccessful()).thenReturn(false, true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Unauthorized", "Success");

        GenericApiResponse<String> apiResponse = okHttpRestClient.sendRequestProtocol(TARGET_ADDRESS, jsonNode,
                () -> "Bearer token", "tenant-1");

        assertNotNull(apiResponse);
        assertTrue(apiResponse.isSuccess());
        verify(credentialUtils).invalidateCachedCredentials();
        verify(okHttpClient, Mockito.times(2)).newCall(argThat(request ->
                "tenant-1".equals(request.header(TenantContextHolder.HEADER_X_TENANT_ID))));
    }

    @Test
    @DisplayName("Send protocol request with Supplier - not sent when no M2M token is available")
    public void sendRequestProtocolSupplier_tokenUnavailable_notSent() {
        Supplier<String> authorizationSupplier = () -> {
            throw new M2mTokenUnavailableException("Token request failed with HTTP 401 (invalid_client)");
        };

        GenericApiResponse<String> apiResponse = okHttpRestClient.sendRequestProtocol(TARGET_ADDRESS, null,
                authorizationSupplier, "tenant-1");

        assertNotNull(apiResponse);
        assertFalse(apiResponse.isSuccess());
        assertEquals(OkHttpRestClient.TOKEN_UNAVAILABLE_MESSAGE, apiResponse.getMessage());
        verify(okHttpClient, Mockito.never()).newCall(any(Request.class));
        verify(credentialUtils, Mockito.never()).invalidateCachedCredentials();
    }

    @Test
    @DisplayName("Send protocol request with Supplier - retry after 401 is not sent when no new token is available")
    public void sendRequestProtocolSupplier_tokenUnavailableOnRetry_notSent() throws IOException {
        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.code()).thenReturn(401);
        when(response.isSuccessful()).thenReturn(false);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("Unauthorized");

        AtomicInteger callCount = new AtomicInteger();
        Supplier<String> authorizationSupplier = () -> {
            if (callCount.incrementAndGet() == 1) {
                return "Bearer stale-token";
            }
            throw new M2mTokenUnavailableException("Token request failed with HTTP 401 (invalid_client)");
        };

        GenericApiResponse<String> apiResponse = okHttpRestClient.sendRequestProtocol(TARGET_ADDRESS, null,
                authorizationSupplier);

        assertFalse(apiResponse.isSuccess());
        assertEquals(OkHttpRestClient.TOKEN_UNAVAILABLE_MESSAGE, apiResponse.getMessage());
        verify(credentialUtils).invalidateCachedCredentials();
        verify(okHttpClient, Mockito.times(1)).newCall(any(Request.class));
    }
}
