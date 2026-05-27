package it.eng.dataplane.httppull;

import com.sun.net.httpserver.HttpServer;
import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.s3.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private ControlPlaneClient controlPlaneClient;

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
            syncExecutor,
            testHttpClient,
            controlPlaneClient
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
    @DisplayName("prepare uses sink metadata view mode to generate presigned URL for the stored processId object")
    void prepareViewModeUsesSinkMetadata() {
        when(s3Properties.getBucketName()).thenReturn("test-bucket");
        when(s3ClientService.generateGetPresignedUrl("test-bucket", "tp-view", Duration.ofDays(7L)))
                .thenReturn("https://example.com/presigned");

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-view")
                .datasetId("dataset-1")
                .metadata(Map.of("sink", Map.of("mode", "VIEW")))
                .build();

        DataFlowPrepareResponse response = protocol.prepare(message);

        assertThat(response.getDataAddress()).containsEntry("presignedUrl", "https://example.com/presigned");
        verify(s3ClientService).generateGetPresignedUrl("test-bucket", "tp-view", Duration.ofDays(7L));
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
        // No CP callbacks when validation fails before transfer starts
        verify(controlPlaneClient, never()).sendStarted(any(), any(), any());
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
        // No CP callbacks when validation fails before transfer starts
        verify(controlPlaneClient, never()).sendStarted(any(), any(), any());
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

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-404")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of(
                        "endpoint", url,
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "test-bucket",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY, "tp-404",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "access",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "secret",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "us-east-1"
                ))
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("404");
        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-404"), anyMap());
        verify(controlPlaneClient).sendErrored(eq("http://cp:8080"), eq("tp-404"), anyString());
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

        when(s3ClientService.uploadFile(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("etag-123"));

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-auth-1")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of(
                        "endpoint", presignedUrl,
                        IConstants.AUTH_TYPE, "Bearer",
                        IConstants.AUTHORIZATION, "test-token-abc",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "test-bucket",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY, "tp-auth-1",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "access-key",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "secret-key",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "us-east-1",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE, "http://minio:9000"
                ))
                .build();

        CompletableFuture<DataFlowResult> resultFuture = protocol.initiateTransfer(dataFlow);
        DataFlowResult result = resultFuture.get();

        assertThat(result.isSuccess()).isTrue();
        assertThat(receivedAuthHeader.get()).isEqualTo("Bearer test-token-abc");
        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-auth-1"), anyMap());
        verify(controlPlaneClient).sendCompleted(eq("http://cp:8080"), eq("tp-auth-1"), anyMap());
    }

    @Test
    @DisplayName("initiateTransfer sends sendStarted then sendCompleted callbacks on successful transfer")
    void initiateTransfer_sendsStartedThenCompletedCallbacks() throws Exception {
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/artifact", exchange -> {
            byte[] body = "artifact-content".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        testHttpServer.start();

        int port = testHttpServer.getAddress().getPort();
        String presignedUrl = "http://localhost:" + port + "/artifact";

        when(s3ClientService.uploadFile(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("etag-ok"));

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-1")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of(
                        "endpoint", presignedUrl,
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "test-bucket",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY, "tp-1",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "access-key",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "secret-key",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "us-east-1",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE, "http://minio:9000"
                ))
                .build();

        protocol.initiateTransfer(dataFlow).join();

        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-1"), anyMap());
        verify(controlPlaneClient).sendCompleted(eq("http://cp:8080"), eq("tp-1"), anyMap());
        verify(controlPlaneClient, never()).sendErrored(any(), any(), any());
    }

    @Test
    @DisplayName("initiateTransfer uses CP-provided sink properties for upload (no DP-local bucket resolution)")
    void initiateTransfer_usesCPProvidedSinkPropertiesForUpload() throws Exception {
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/artifact", exchange -> {
            byte[] body = "artifact-content".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        testHttpServer.start();

        int port = testHttpServer.getAddress().getPort();
        String presignedUrl = "http://localhost:" + port + "/artifact";

        when(s3ClientService.uploadFile(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("etag-cp"));

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-cp-sink-1")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of(
                        "endpoint", presignedUrl,
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "cp-provided-bucket",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY, "cp-provided-key",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "cp-access-key",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "cp-secret-key",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "eu-west-1",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE, "http://cp-minio:9000"
                ))
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> s3PropsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(s3ClientService).uploadFile(any(), s3PropsCaptor.capture(), any(), any());

        Map<String, String> s3Props = s3PropsCaptor.getValue();
        assertThat(s3Props)
                .containsEntry(S3Utils.BUCKET_NAME, "cp-provided-bucket")
                .containsEntry(S3Utils.OBJECT_KEY, "cp-provided-key")
                .containsEntry(S3Utils.ACCESS_KEY, "cp-access-key")
                .containsEntry(S3Utils.SECRET_KEY, "cp-secret-key")
                .containsEntry(S3Utils.REGION, "eu-west-1")
                .containsEntry(S3Utils.ENDPOINT_OVERRIDE, "http://cp-minio:9000");
    }

    @Test
    @DisplayName("initiateTransfer sends sendStarted then sendErrored callbacks on upload failure")
    void initiateTransfer_sendsStartedThenErroredCallbacksOnFailure() throws Exception {
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/artifact", exchange -> {
            byte[] body = "artifact-content".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        testHttpServer.start();

        int port = testHttpServer.getAddress().getPort();
        String presignedUrl = "http://localhost:" + port + "/artifact";

        when(s3ClientService.uploadFile(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("S3 upload failed")));

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-fail-1")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of(
                        "endpoint", presignedUrl,
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "test-bucket",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY, "tp-fail-1",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "access-key",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "secret-key",
                        DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "us-east-1"
                ))
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-fail-1"), anyMap());
        verify(controlPlaneClient).sendErrored(eq("http://cp:8080"), eq("tp-fail-1"), anyString());
        verify(controlPlaneClient, never()).sendCompleted(any(), any(), any());
    }
}
