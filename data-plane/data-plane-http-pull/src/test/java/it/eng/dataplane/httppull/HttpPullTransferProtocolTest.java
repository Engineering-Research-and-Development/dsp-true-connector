package it.eng.dataplane.httppull;

import com.sun.net.httpserver.HttpServer;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.s3.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.dataplane.s3.service.TenantBucketResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HttpPullTransferProtocol}.
 */
@ExtendWith(MockitoExtension.class)
class HttpPullTransferProtocolTest {

    @Mock
    private S3ClientService s3ClientService;
    @Mock
    private S3Properties s3Properties;
    @Mock
    private TenantBucketResolver tenantBucketResolver;

    private HttpPullTransferProtocol protocol;
    private HttpServer testHttpServer;

    // Synchronous executor for testing — runs tasks immediately in the calling thread
    private final Executor syncExecutor = Runnable::run;

    // Plain HTTP/1.1 client for tests — test server uses plain HTTP, no TLS needed
    private final HttpClient testHttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void setUp() {
        protocol = new HttpPullTransferProtocol(
            s3ClientService,
            s3Properties,
            tenantBucketResolver,
            syncExecutor,
            testHttpClient
        );
    }

    @AfterEach
    void tearDown() {
        if (testHttpServer != null) {
            testHttpServer.stop(0);
        }
    }

    @Test
    @DisplayName("getProtocolId returns HttpData-PULL")
    void protocolIdIsHttpDataPull() {
        assertThat(protocol.getProtocolId()).isEqualTo("HttpData-PULL");
    }

    @Test
    @DisplayName("initiateTransfer returns failure when endpoint is missing from dataAddress")
    void initiateTransferReturnsFailureWhenEndpointMissing() throws Exception {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("tp-1")
            .transferType("HttpData-PULL")
            .dataAddress(Map.of()) // no endpoint key
            .build();

        CompletableFuture<DataFlowResult> resultFuture = protocol.initiateTransfer(dataFlow);
        DataFlowResult result = resultFuture.get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("endpoint");
    }

    @Test
    @DisplayName("initiateTransfer returns failure when dataAddress is null")
    void initiateTransferReturnsFailureWhenDataAddressIsNull() throws Exception {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("tp-2")
            .transferType("HttpData-PULL")
            .dataAddress(null)
            .build();

        CompletableFuture<DataFlowResult> resultFuture = protocol.initiateTransfer(dataFlow);
        DataFlowResult result = resultFuture.get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("endpoint");
    }

    @Test
    @DisplayName("suspendTransfer returns failure with 'suspend not supported' message")
    void suspendTransferReturnsFailure() throws Exception {
        CompletableFuture<DataFlowResult> resultFuture = protocol.suspendTransfer("df-1");
        DataFlowResult result = resultFuture.get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("suspend not supported");
    }

    @Test
    @DisplayName("resumeTransfer returns failure with 'resume not supported' message")
    void resumeTransferReturnsFailure() throws Exception {
        CompletableFuture<DataFlowResult> resultFuture = protocol.resumeTransfer("df-1");
        DataFlowResult result = resultFuture.get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("resume not supported");
    }

    @Test
    @DisplayName("terminateTransfer returns success")
    void terminateTransferReturnsSuccess() throws Exception {
        CompletableFuture<DataFlowResult> resultFuture = protocol.terminateTransfer("df-1");
        DataFlowResult result = resultFuture.get();

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("initiateTransfer returns failure when server responds with non-200 status")
    void initiateTransfer_returnsFailureOnNon200Response() throws Exception {
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/not-found", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        testHttpServer.start();

        String url = "http://localhost:" + testHttpServer.getAddress().getPort() + "/not-found";
        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("test-bucket");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-404")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .dataAddress(Map.of("endpoint", url))
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("404");
    }

    @Test
    @DisplayName("initiateTransfer sets Authorization header from dataAddress when AUTH_TYPE and AUTHORIZATION are present")
    void initiateTransfer_setsAuthorizationHeader() throws Exception {
        // Capture the Authorization header received by the test server
        AtomicReference<String> receivedAuthHeader = new AtomicReference<>();

        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/artifact", exchange -> {
            receivedAuthHeader.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            byte[] body = "file-content".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        testHttpServer.start();

        int port = testHttpServer.getAddress().getPort();
        String presignedUrl = "http://localhost:" + port + "/artifact";

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("test-bucket");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("access-key");
        when(s3Properties.getSecretKey()).thenReturn("secret-key");
        when(s3ClientService.uploadFile(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("etag-123"));

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-auth-1")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .dataAddress(Map.of(
                        "endpoint", presignedUrl,
                        IConstants.AUTH_TYPE, "Bearer",
                        IConstants.AUTHORIZATION, "test-token-abc"
                ))
                .build();

        CompletableFuture<DataFlowResult> resultFuture = protocol.initiateTransfer(dataFlow);
        DataFlowResult result = resultFuture.get();

        assertThat(result.isSuccess()).isTrue();
        assertThat(receivedAuthHeader.get()).isEqualTo("Bearer test-token-abc");
    }
}
