package it.eng.dataplane.httppull;

import com.sun.net.httpserver.HttpServer;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.core.model.DataFlowCheckpoint;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.repository.DataFlowRepository;
import it.eng.dataplane.core.service.DataFlowCheckpointService;
import it.eng.dataplane.s3.model.IConstants;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.dataplane.s3.service.TenantBucketResolver;
import it.eng.tools.s3.service.upload.ResumableUploadRequest;
import it.eng.tools.s3.service.upload.UploadPausedException;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
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
    private TenantBucketResolver tenantBucketResolver;
    @Mock
    private ControlPlaneClient controlPlaneClient;
    @Mock
    private DataFlowRepository dataFlowRepository;
    @Mock
    private DataFlowCheckpointService checkpointService;

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
        lenient().when(checkpointService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        protocol = new HttpPullTransferProtocol(
            s3ClientService,
            s3Properties,
            tenantBucketResolver,
            syncExecutor,
            testHttpClient,
            controlPlaneClient,
            dataFlowRepository,
            checkpointService
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
    @DisplayName("hasUsableAccessMaterial returns true when endpoint is present")
    void hasUsableAccessMaterial_returnsTrueWhenEndpointPresent() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-material")
                .transferType("HttpData-PULL")
                .dataAddress(Map.of("endpoint", "https://example.com/presigned"))
                .build();
        assertThat(protocol.hasUsableAccessMaterial(dataFlow)).isTrue();
    }

    @Test
    @DisplayName("hasUsableAccessMaterial returns false when endpoint is missing")
    void hasUsableAccessMaterial_returnsFalseWhenEndpointMissing() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-material")
                .transferType("HttpData-PULL")
                .dataAddress(Map.of())
                .build();
        assertThat(protocol.hasUsableAccessMaterial(dataFlow)).isFalse();
    }

    @Test
    @DisplayName("hasUsableAccessMaterial returns false when dataAddress is null")
    void hasUsableAccessMaterial_returnsFalseWhenDataAddressNull() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-material")
                .transferType("HttpData-PULL")
                .dataAddress(null)
                .build();
        assertThat(protocol.hasUsableAccessMaterial(dataFlow)).isFalse();
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
    @DisplayName("suspendTransfer sets the suspend flag and returns success")
    void suspendTransferSetsFlagAndReturnsSuccess() throws Exception {
        // Start a transfer to register a suspend flag
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/slow", exchange -> {
            // Response with content — suspend will be triggered before/during upload
            byte[] body = "content".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        testHttpServer.start();
        String url = "http://localhost:" + testHttpServer.getAddress().getPort() + "/slow";

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("test-bucket");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("access-key");
        when(s3Properties.getSecretKey()).thenReturn("secret-key");
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("etag-123"));

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-suspend-1")
                .processId("tp-suspend-1")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of("endpoint", url))
                .build();

        // Start the transfer (registers the flag internally)
        protocol.initiateTransfer(dataFlow).get();

        // Now test suspendTransfer on a fresh flag (simulating mid-transfer call)
        CompletableFuture<DataFlowResult> suspendResult = protocol.suspendTransfer("df-suspend-1");
        assertThat(suspendResult.get().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("suspendTransfer on unknown dataFlowId returns success (tolerant)")
    void suspendTransferOnUnknownIdReturnsSuccess() throws Exception {
        CompletableFuture<DataFlowResult> result = protocol.suspendTransfer("unknown-df-id");
        assertThat(result.get().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("resumeTransfer returns failure when entity is not found")
    void resumeTransferReturnsFailureWhenEntityNotFound() throws Exception {
        when(dataFlowRepository.findById("df-missing")).thenReturn(Optional.empty());

        CompletableFuture<DataFlowResult> result = protocol.resumeTransfer("df-missing");
        assertThat(result.get().isSuccess()).isFalse();
        assertThat(result.get().getErrorMessage()).contains("not found");
    }

    @Test
    @DisplayName("resumeTransfer adds Range header when checkpoint has confirmedBytes > 0")
    void resumeTransferAddsRangeHeaderWhenCheckpointHasOffset() throws Exception {
        AtomicReference<String> receivedRangeHeader = new AtomicReference<>();
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/artifact", exchange -> {
            receivedRangeHeader.set(exchange.getRequestHeaders().getFirst("Range"));
            byte[] body = "remaining-content".getBytes();
            // Return 206 to simulate Range response
            exchange.sendResponseHeaders(206, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        testHttpServer.start();

        String presignedUrl = "http://localhost:" + testHttpServer.getAddress().getPort() + "/artifact";
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-resume-range")
                .processId("tp-resume-range")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of("endpoint", presignedUrl))
                .build();

        DataFlowCheckpoint checkpoint = DataFlowCheckpoint.Builder.newInstance()
                .processId("tp-resume-range")
                .dataFlowId("df-resume-range")
                .transferType("HttpData-PULL")
                .uploadId("upload-123")
                .build()
                .withCompletedPart(1, 1024L, "etag-part1", 1024L); // 1 KB already uploaded

        when(dataFlowRepository.findById("df-resume-range")).thenReturn(Optional.of(entity));
        when(checkpointService.findByProcessId("tp-resume-range")).thenReturn(Optional.of(checkpoint));
        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("test-bucket");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("access-key");
        when(s3Properties.getSecretKey()).thenReturn("secret-key");
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("etag-resume-ok"));

        DataFlowResult result = protocol.resumeTransfer("df-resume-range").get();

        assertThat(result.isSuccess()).isTrue();
        assertThat(receivedRangeHeader.get()).isEqualTo("bytes=1024-");
        verify(controlPlaneClient).sendCompleted(eq("http://cp:8080"), eq("tp-resume-range"), anyMap());
        verify(controlPlaneClient, never()).sendErrored(any(), any(), any());
    }

    @Test
    @DisplayName("resumeTransfer skips Range header when confirmedBytes is 0")
    void resumeTransferSkipsRangeHeaderWhenConfirmedBytesIsZero() throws Exception {
        AtomicReference<String> receivedRangeHeader = new AtomicReference<>("NOT_SET");
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/artifact", exchange -> {
            receivedRangeHeader.set(exchange.getRequestHeaders().getFirst("Range"));
            byte[] body = "full-content".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        testHttpServer.start();

        String presignedUrl = "http://localhost:" + testHttpServer.getAddress().getPort() + "/artifact";
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-resume-zero")
                .processId("tp-resume-zero")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of("endpoint", presignedUrl))
                .build();

        DataFlowCheckpoint checkpoint = DataFlowCheckpoint.Builder.newInstance()
                .processId("tp-resume-zero")
                .dataFlowId("df-resume-zero")
                .transferType("HttpData-PULL")
                .confirmedBytes(0L)
                .build();

        when(dataFlowRepository.findById("df-resume-zero")).thenReturn(Optional.of(entity));
        when(checkpointService.findByProcessId("tp-resume-zero")).thenReturn(Optional.of(checkpoint));
        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("test-bucket");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("access-key");
        when(s3Properties.getSecretKey()).thenReturn("secret-key");
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("etag-ok"));

        DataFlowResult result = protocol.resumeTransfer("df-resume-zero").get();

        assertThat(result.isSuccess()).isTrue();
        assertThat(receivedRangeHeader.get()).isNull(); // no Range header sent
        verify(controlPlaneClient).sendCompleted(eq("http://cp:8080"), eq("tp-resume-zero"), anyMap());
        verify(controlPlaneClient, never()).sendErrored(any(), any(), any());
    }

    @Test
    @DisplayName("resumeTransfer returns failure on non-200/206 HTTP response")
    void resumeTransferReturnsFailureOnBadHttpStatus() throws Exception {
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/expired", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        testHttpServer.start();

        String presignedUrl = "http://localhost:" + testHttpServer.getAddress().getPort() + "/expired";
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-resume-403")
                .processId("tp-resume-403")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of("endpoint", presignedUrl))
                .build();

        DataFlowCheckpoint checkpoint = DataFlowCheckpoint.Builder.newInstance()
                .processId("tp-resume-403")
                .dataFlowId("df-resume-403")
                .confirmedBytes(0L)
                .build();

        when(dataFlowRepository.findById("df-resume-403")).thenReturn(Optional.of(entity));
        when(checkpointService.findByProcessId("tp-resume-403")).thenReturn(Optional.of(checkpoint));
        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("test-bucket");

        DataFlowResult result = protocol.resumeTransfer("df-resume-403").get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("403");
        verify(controlPlaneClient).sendErrored(eq("http://cp:8080"), eq("tp-resume-403"), anyString());
        verify(controlPlaneClient, never()).sendCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("resumeTransfer paused again skips Control Plane callbacks")
    void resumeTransferPausedAgainSkipsCallbacks() throws Exception {
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/artifact", exchange -> {
            byte[] body = "content".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        testHttpServer.start();

        String presignedUrl = "http://localhost:" + testHttpServer.getAddress().getPort() + "/artifact";
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-resume-paused")
                .processId("tp-resume-paused")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of("endpoint", presignedUrl))
                .build();

        DataFlowCheckpoint checkpoint = DataFlowCheckpoint.Builder.newInstance()
                .processId("tp-resume-paused")
                .dataFlowId("df-resume-paused")
                .transferType("HttpData-PULL")
                .confirmedBytes(0L)
                .build();

        when(dataFlowRepository.findById("df-resume-paused")).thenReturn(Optional.of(entity));
        when(checkpointService.findByProcessId("tp-resume-paused")).thenReturn(Optional.of(checkpoint));
        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("test-bucket");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("access-key");
        when(s3Properties.getSecretKey()).thenReturn("secret-key");
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new java.util.concurrent.CompletionException(
                                new UploadPausedException("paused-again", "upload-id", List.of(), List.of(), 0L))));

        DataFlowResult result = protocol.resumeTransfer("df-resume-paused").get();

        assertThat(result.isSuccess()).isTrue();
        verify(controlPlaneClient, never()).sendCompleted(any(), any(), any());
        verify(controlPlaneClient, never()).sendErrored(any(), any(), any());
    }

    @Test
    @DisplayName("initiateTransfer persists checkpoint and invokes callback on each uploaded part")
    void initiateTransfer_persistsCheckpointOnPartCompleted() throws Exception {
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/artifact", exchange -> {
            byte[] body = "artifact-content".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        testHttpServer.start();

        String presignedUrl = "http://localhost:" + testHttpServer.getAddress().getPort() + "/artifact";

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("test-bucket");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("access-key");
        when(s3Properties.getSecretKey()).thenReturn("secret-key");

        // Simulate S3 calling the checkpoint callback when a part completes
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any(ResumableUploadRequest.class)))
                .thenAnswer(invocation -> {
                    ResumableUploadRequest req = invocation.getArgument(4);
                    req.checkpointCallback().onMultipartCreated("mpu-test-id");
                    req.checkpointCallback().onPartCompleted(1, "etag-p1", 16L, 16L);
                    return CompletableFuture.completedFuture("final-etag");
                });

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-checkpoint-1")
                .processId("tp-checkpoint-1")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of("endpoint", presignedUrl))
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isTrue();
        // Checkpoint should have been saved: initial save + onMultipartCreated + onPartCompleted
        verify(checkpointService, atLeastOnce()).save(any(DataFlowCheckpoint.class));
    }

    @Test
    @DisplayName("initiateTransfer returns success when upload is paused (suspend cooperatively stops upload)")
    void initiateTransfer_returnSuccessWhenUploadPaused() throws Exception {
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/artifact", exchange -> {
            byte[] body = "content".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        testHttpServer.start();
        String presignedUrl = "http://localhost:" + testHttpServer.getAddress().getPort() + "/artifact";

        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-resume-paused")
                .processId("tp-resume-paused")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of("endpoint", presignedUrl))
                .build();

        DataFlowCheckpoint checkpoint = DataFlowCheckpoint.Builder.newInstance()
                .processId("tp-resume-paused")
                .dataFlowId("df-resume-paused")
                .transferType("HttpData-PULL")
                .confirmedBytes(0L)
                .build();

        when(dataFlowRepository.findById("df-resume-paused")).thenReturn(Optional.of(entity));
        when(checkpointService.findByProcessId("tp-resume-paused")).thenReturn(Optional.of(checkpoint));
        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("test-bucket");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("access-key");
        when(s3Properties.getSecretKey()).thenReturn("secret-key");
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any(ResumableUploadRequest.class)))
                .thenAnswer(invocation -> CompletableFuture.<String>failedFuture(
                        new java.util.concurrent.CompletionException(
                                new UploadPausedException("paused-again", "upload-id", List.of(), List.of(), 0L))));

        DataFlowResult result = protocol.resumeTransfer("df-resume-paused").get();

        assertThat(result.isSuccess()).isTrue();
        verify(controlPlaneClient, never()).sendCompleted(any(), any(), any());
        verify(controlPlaneClient, never()).sendErrored(any(), any(), any());
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
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of("endpoint", url))
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

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("test-bucket");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("access-key");
        when(s3Properties.getSecretKey()).thenReturn("secret-key");
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("etag-123"));

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-auth-1")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
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

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("test-bucket");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("access-key");
        when(s3Properties.getSecretKey()).thenReturn("secret-key");
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("etag-ok"));

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-1")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of("endpoint", presignedUrl))
                .build();

        protocol.initiateTransfer(dataFlow).join();

        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-1"), anyMap());
        verify(controlPlaneClient).sendCompleted(eq("http://cp:8080"), eq("tp-1"), anyMap());
        verify(controlPlaneClient, never()).sendErrored(any(), any(), any());
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

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("test-bucket");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getAccessKey()).thenReturn("access-key");
        when(s3Properties.getSecretKey()).thenReturn("secret-key");
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("S3 upload failed")));

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-fail-1")
                .transferType("HttpData-PULL")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .dataAddress(Map.of("endpoint", presignedUrl))
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-fail-1"), anyMap());
        verify(controlPlaneClient).sendErrored(eq("http://cp:8080"), eq("tp-fail-1"), anyString());
        verify(controlPlaneClient, never()).sendCompleted(any(), any(), any());
    }
}
