package it.eng.dataplane.httppush;

import com.sun.net.httpserver.HttpServer;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.core.model.DataFlowCheckpoint;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.repository.DataFlowRepository;
import it.eng.dataplane.core.service.DataFlowCheckpointService;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.s3.service.upload.ResumableUploadRequest;
import it.eng.tools.s3.service.upload.UploadPausedException;
import it.eng.tools.s3.util.S3Utils;
import it.eng.dataplane.s3.service.TenantBucketResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link HttpPushTransferProtocol}.
 */
@ExtendWith(MockitoExtension.class)
class HttpPushTransferProtocolTest {

    @Mock
    private S3ClientService s3ClientService;
    @Mock
    private TemporaryBucketUserService temporaryBucketUserService;
    @Mock
    private TenantBucketResolver tenantBucketResolver;
    @Mock
    private S3Properties s3Properties;
    @Mock
    private ControlPlaneClient controlPlaneClient;
    @Mock
    private DataFlowCheckpointService checkpointService;
    @Mock
    private DataFlowRepository dataFlowRepository;

    private HttpPushTransferProtocol protocol;
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
        protocol = new HttpPushTransferProtocol(
            s3ClientService,
            s3Properties,
            temporaryBucketUserService,
            tenantBucketResolver,
            syncExecutor,
            testHttpClient,
            controlPlaneClient,
            checkpointService,
            dataFlowRepository
        );
    }

    @AfterEach
    void tearDown() {
        if (testHttpServer != null) {
            testHttpServer.stop(0);
        }
    }

    @Test
    @DisplayName("getProtocolId returns HttpData-PUSH")
    void getProtocolId_returnsHttpDataPush() {
        assertThat(protocol.getProtocolId()).isEqualTo("HttpData-PUSH");
    }

    @Test
    @DisplayName("initiateTransfer returns failure when dataAddress is empty (no bucketName)")
    void initiateTransfer_failsWhenDataAddressEmpty() throws Exception {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("tp-1")
            .transferType("HttpData-PUSH")
            .dataAddress(Map.of())
            .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("bucketName");
        // No CP callbacks when validation fails before transfer starts
        verify(controlPlaneClient, never()).sendStarted(any(), any(), any());
    }

    @Test
    @DisplayName("initiateTransfer returns failure when dataAddress is null")
    void initiateTransfer_failsWhenDataAddressNull() throws Exception {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("tp-2")
            .transferType("HttpData-PUSH")
            .dataAddress(null)
            .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("bucketName");
        // No CP callbacks when validation fails before transfer starts
        verify(controlPlaneClient, never()).sendStarted(any(), any(), any());
    }

    @Test
    @DisplayName("suspendTransfer returns success when no active flag exists")
    void suspendTransfer_returnsSuccessWhenNoActiveFlag() throws Exception {
        when(dataFlowRepository.findById("df-1")).thenReturn(Optional.empty());

        DataFlowResult result = protocol.suspendTransfer("df-1").get();

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("suspendTransfer returns success and sets active flag when entity is found")
    void suspendTransfer_setsActiveFlagWhenEntityFound() throws Exception {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-1")
                .processId("tp-suspend")
                .state(DataFlowState.STARTED)
                .build();
        when(dataFlowRepository.findById("df-1")).thenReturn(Optional.of(entity));

        DataFlowResult result = protocol.suspendTransfer("df-1").get();

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("resumeTransfer returns failure when no entity is found")
    void resumeTransfer_failsWhenNoEntityFound() throws Exception {
        when(dataFlowRepository.findById("df-1")).thenReturn(Optional.empty());

        DataFlowResult result = protocol.resumeTransfer("df-1").get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("df-1");
    }

    @Test
    @DisplayName("resumeTransfer returns failure when no checkpoint exists")
    void resumeTransfer_failsWhenNoCheckpoint() throws Exception {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-1")
                .processId("tp-resume")
                .state(DataFlowState.SUSPENDED)
                .dataAddress(Map.of(S3Utils.BUCKET_NAME, "consumer-bucket",
                        S3Utils.ACCESS_KEY, "acc", S3Utils.SECRET_KEY, "sec"))
                .build();
        when(dataFlowRepository.findById("df-1")).thenReturn(Optional.of(entity));
        when(checkpointService.findByProcessId("tp-resume")).thenReturn(Optional.empty());

        DataFlowResult result = protocol.resumeTransfer("df-1").get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("checkpoint");
    }

    @Test
    @DisplayName("resumeTransfer reuses consumer credentials from entity dataAddress — no new temp user")
    void resumeTransfer_reusesConsumerCredentials() throws Exception {
        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(S3Utils.BUCKET_NAME, "consumer-bucket");
        dataAddress.put(S3Utils.ACCESS_KEY, "consumer-access");
        dataAddress.put(S3Utils.SECRET_KEY, "consumer-secret");
        dataAddress.put(S3Utils.OBJECT_KEY, "tp-resume");
        dataAddress.put(S3Utils.REGION, "us-east-1");

        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-1")
                .processId("tp-resume")
                .datasetId("dataset-1")
                .transferType("HttpData-PUSH")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .state(DataFlowState.SUSPENDED)
                .dataAddress(dataAddress)
                .build();
        when(dataFlowRepository.findById("df-1")).thenReturn(Optional.of(entity));

        DataFlowCheckpoint checkpoint = DataFlowCheckpoint.Builder.newInstance()
                .processId("tp-resume")
                .dataFlowId("df-1")
                .uploadId("upload-id-1")
                .destinationBucket("consumer-bucket")
                .destinationObjectKey("tp-resume")
                .completedParts(List.of())
                .partSizes(Map.of())
                .partETags(Map.of())
                .confirmedBytes(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(checkpointService.findByProcessId("tp-resume")).thenReturn(Optional.of(checkpoint));

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

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("provider-bucket");
        when(s3ClientService.generateGetPresignedUrl(anyString(), anyString(), any(Duration.class)))
                .thenReturn("http://localhost:" + port + "/artifact");
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any(ResumableUploadRequest.class)))
                .thenReturn(CompletableFuture.completedFuture("etag-resume"));

        DataFlowResult result = protocol.resumeTransfer("df-1").get();

        assertThat(result.isSuccess()).isTrue();
        // Must NOT create a new temporary IAM user — credentials are reused from dataAddress
        verify(temporaryBucketUserService, never()).createTemporaryUser(any(), any(), any());
        verify(controlPlaneClient).sendCompleted(eq("http://cp:8080"), eq("tp-resume"), anyMap());
        verify(checkpointService).deleteByProcessId("tp-resume");
    }

    @Test
    @DisplayName("hasUsableAccessMaterial returns true when dataAddress has bucket, accessKey and secretKey")
    void hasUsableAccessMaterial_returnsTrueWhenCredentialsPresent() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-check")
                .transferType("HttpData-PUSH")
                .dataAddress(Map.of(
                        S3Utils.BUCKET_NAME, "consumer-bucket",
                        S3Utils.ACCESS_KEY, "acc",
                        S3Utils.SECRET_KEY, "sec"
                ))
                .build();

        assertThat(protocol.hasUsableAccessMaterial(dataFlow)).isTrue();
    }

    @Test
    @DisplayName("hasUsableAccessMaterial returns false when dataAddress is missing secretKey")
    void hasUsableAccessMaterial_returnsFalseWhenSecretKeyMissing() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-check")
                .transferType("HttpData-PUSH")
                .dataAddress(Map.of(
                        S3Utils.BUCKET_NAME, "consumer-bucket",
                        S3Utils.ACCESS_KEY, "acc"
                ))
                .build();

        assertThat(protocol.hasUsableAccessMaterial(dataFlow)).isFalse();
    }

    @Test
    @DisplayName("hasUsableAccessMaterial returns false when dataAddress is missing bucketName")
    void hasUsableAccessMaterial_returnsFalseWhenBucketNameMissing() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-check")
                .transferType("HttpData-PUSH")
                .dataAddress(Map.of(
                        S3Utils.ACCESS_KEY, "acc",
                        S3Utils.SECRET_KEY, "sec"
                ))
                .build();

        assertThat(protocol.hasUsableAccessMaterial(dataFlow)).isFalse();
    }

    @Test
    @DisplayName("terminateTransfer returns success without throwing")
    void terminateTransfer_returnsSuccess() throws Exception {
        when(dataFlowRepository.findById("df-1")).thenReturn(Optional.empty());

        DataFlowResult result = protocol.terminateTransfer("df-1").get();

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("terminateTransfer resolves processId from entity to remove the correct suspend flag")
    void terminateTransfer_removesActiveSuspendFlagByProcessId() throws Exception {
        DataFlowEntity entity = DataFlowEntity.Builder.newInstance()
                .id("df-term")
                .processId("tp-term")
                .state(DataFlowState.STARTED)
                .build();
        when(dataFlowRepository.findById("df-term")).thenReturn(Optional.of(entity));

        DataFlowResult result = protocol.terminateTransfer("df-term").get();

        assertThat(result.isSuccess()).isTrue();
        // The entity lookup must use the dataFlowId, not the processId
        verify(dataFlowRepository).findById("df-term");
    }

    @Test
    @DisplayName("initiateTransfer does not call sendCompleted when upload is paused (UploadPausedException)")
    void initiateTransfer_doesNotSendCompletedOnPause() throws Exception {
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/artifact-pause", exchange -> {
            byte[] body = "artifact-content".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        testHttpServer.start();
        int port = testHttpServer.getAddress().getPort();

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("provider-bucket");
        when(s3ClientService.generateGetPresignedUrl(anyString(), anyString(), any(Duration.class)))
                .thenReturn("http://localhost:" + port + "/artifact-pause");

        UploadPausedException pauseEx = new UploadPausedException("paused", "upload-id-pause",
                List.of(), List.of(), 0L);
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any(ResumableUploadRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(pauseEx));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(S3Utils.BUCKET_NAME, "consumer-bucket");
        dataAddress.put(S3Utils.ACCESS_KEY, "consumer-access");
        dataAddress.put(S3Utils.SECRET_KEY, "plain-secret");
        dataAddress.put(S3Utils.REGION, "us-east-1");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-pause")
                .transferType("HttpData-PUSH")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .datasetId("dataset-pause")
                .dataAddress(dataAddress)
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        // The pause path must return a paused result, never a success
        assertThat(result.isPaused()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        // sendCompleted MUST NOT be called — a pause is not a completion
        verify(controlPlaneClient, never()).sendCompleted(any(), any(), any());
        // sendErrored MUST NOT be called — a pause is not an error
        verify(controlPlaneClient, never()).sendErrored(any(), any(), any());
    }

    @Test
    @DisplayName("initiateTransfer returns failure when presigned URL returns non-200 status")
    void initiateTransfer_returnsFailureOnNon200PresignedResponse() throws Exception {
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpServer.createContext("/artifact-403", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        testHttpServer.start();

        int port = testHttpServer.getAddress().getPort();
        String presignedUrl = "http://localhost:" + port + "/artifact-403";

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("provider-bucket");
        when(s3ClientService.generateGetPresignedUrl(eq("provider-bucket"), anyString(), any(Duration.class)))
            .thenReturn(presignedUrl);

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(S3Utils.BUCKET_NAME, "consumer-bucket");
        dataAddress.put(S3Utils.ACCESS_KEY, "consumer-access");
        dataAddress.put(S3Utils.SECRET_KEY, "plain-secret");
        dataAddress.put(S3Utils.REGION, "us-east-1");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-403")
                .transferType("HttpData-PUSH")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .datasetId("dataset-403")
                .dataAddress(dataAddress)
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("403");
        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-403"), anyMap());
        verify(controlPlaneClient).sendErrored(eq("http://cp:8080"), eq("tp-403"), anyString());
    }

    @Test
    @DisplayName("initiateTransfer pushes artifact to consumer S3 and sends started/completed callbacks")
    void initiateTransfer_successfulPushToConsumerS3() throws Exception {
        // Serve dummy artifact content from a local HTTP server
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

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("provider-bucket");
        when(s3ClientService.generateGetPresignedUrl(eq("provider-bucket"), anyString(), any(Duration.class)))
            .thenReturn(presignedUrl);
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any(ResumableUploadRequest.class)))
            .thenReturn(CompletableFuture.completedFuture("etag-xyz"));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(S3Utils.BUCKET_NAME, "consumer-bucket");
        dataAddress.put(S3Utils.OBJECT_KEY, "tp-obj-key");
        dataAddress.put(S3Utils.ACCESS_KEY, "consumer-access");
        dataAddress.put(S3Utils.SECRET_KEY, "plain-secret");
        dataAddress.put(S3Utils.ENDPOINT_OVERRIDE, "http://consumer-minio:9000");
        dataAddress.put(S3Utils.REGION, "us-east-1");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("tp-3")
            .transferType("HttpData-PUSH")
            .tenantId("tenant-1")
            .callbackAddress("http://cp:8080")
            .datasetId("dataset-1")
            .dataAddress(dataAddress)
            .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isTrue();
        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-3"), anyMap());
        verify(controlPlaneClient).sendCompleted(eq("http://cp:8080"), eq("tp-3"), anyMap());
        verify(controlPlaneClient, never()).sendErrored(any(), any(), any());
        verify(temporaryBucketUserService, never()).deleteTemporaryUser(any());
    }

    @Test
    @DisplayName("initiateTransfer persists contiguous confirmed bytes from checkpoint callbacks")
    void initiateTransfer_persistsContiguousConfirmedBytesFromCheckpointCallbacks() throws Exception {
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

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("provider-bucket");
        when(s3ClientService.generateGetPresignedUrl(eq("provider-bucket"), anyString(), any(Duration.class)))
                .thenReturn(presignedUrl);
        when(checkpointService.save(any(DataFlowCheckpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any(ResumableUploadRequest.class)))
                .thenAnswer(invocation -> {
                    ResumableUploadRequest resumableRequest = invocation.getArgument(4);
                    resumableRequest.checkpointCallback().onMultipartCreated("upload-id-1");
                    resumableRequest.checkpointCallback().onPartCompleted(2, "etag-2", 10L, 0L);
                    return CompletableFuture.completedFuture("etag-ok");
                });

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(S3Utils.BUCKET_NAME, "consumer-bucket");
        dataAddress.put(S3Utils.ACCESS_KEY, "consumer-access");
        dataAddress.put(S3Utils.SECRET_KEY, "plain-secret");
        dataAddress.put(S3Utils.REGION, "us-east-1");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-contiguous")
                .transferType("HttpData-PUSH")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .datasetId("dataset-1")
                .dataAddress(dataAddress)
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<DataFlowCheckpoint> checkpointCaptor = ArgumentCaptor.forClass(DataFlowCheckpoint.class);
        verify(checkpointService, times(2)).save(checkpointCaptor.capture());
        DataFlowCheckpoint completedPartCheckpoint = checkpointCaptor.getAllValues().get(1);
        assertThat(completedPartCheckpoint.getCompletedParts()).containsExactly(2);
        assertThat(completedPartCheckpoint.getConfirmedBytes()).isZero();
    }

    @Test
    @DisplayName("initiateTransfer sends started/completed callbacks and uses processId as S3 objectKey when objectKey is absent from dataAddress")
    void initiateTransfer_usesProcessIdAsObjectKeyFallback() throws Exception {
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

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("provider-bucket");
        when(s3ClientService.generateGetPresignedUrl(anyString(), anyString(), any(Duration.class)))
            .thenReturn(presignedUrl);
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any(ResumableUploadRequest.class)))
            .thenReturn(CompletableFuture.completedFuture("etag-xyz"));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(S3Utils.BUCKET_NAME, "consumer-bucket");
        dataAddress.put(S3Utils.ACCESS_KEY, "consumer-access");
        dataAddress.put(S3Utils.SECRET_KEY, "plain-secret");
        dataAddress.put(S3Utils.REGION, "us-east-1");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("tp-4")
            .transferType("HttpData-PUSH")
            .tenantId("tenant-1")
            .callbackAddress("http://cp:8080")
            .datasetId("dataset-1")
            .dataAddress(dataAddress)
            .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isTrue();
        // Verify processId was used as the S3 objectKey since it was absent from dataAddress
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> s3PropsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(s3ClientService).uploadFile(any(), s3PropsCaptor.capture(), any(), any(), any(ResumableUploadRequest.class));
        assertThat(s3PropsCaptor.getValue()).containsEntry(S3Utils.OBJECT_KEY, "tp-4");
        // Cleanup of temporary credentials is handled by the consumer CP, not the provider-side push DP
        verify(temporaryBucketUserService, never()).deleteTemporaryUser(any());
        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-4"), anyMap());
        verify(controlPlaneClient).sendCompleted(eq("http://cp:8080"), eq("tp-4"), anyMap());
    }

    @Test
    @DisplayName("initiateTransfer does not clean up temporary credentials on upload failure — consumer CP handles cleanup")
    void initiateTransfer_cleansUpCredentialsOnUploadFailure() throws Exception {
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

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("provider-bucket");
        when(s3ClientService.generateGetPresignedUrl(anyString(), anyString(), any(Duration.class)))
            .thenReturn(presignedUrl);
        // Simulate upload failure
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any(ResumableUploadRequest.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("S3 auth failure")));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(S3Utils.BUCKET_NAME, "consumer-bucket");
        dataAddress.put(S3Utils.ACCESS_KEY, "consumer-access");
        dataAddress.put(S3Utils.SECRET_KEY, "plain-secret");
        dataAddress.put(S3Utils.REGION, "us-east-1");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("tp-5")
            .transferType("HttpData-PUSH")
            .tenantId("tenant-1")
            .callbackAddress("http://cp:8080")
            .datasetId("dataset-1")
            .dataAddress(dataAddress)
            .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("S3 auth failure");
        // Cleanup of temporary credentials is the consumer CP's responsibility,
        // not the provider-side push DP — even when upload fails.
        verify(temporaryBucketUserService, never()).deleteTemporaryUser(any());
        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-5"), anyMap());
        verify(controlPlaneClient).sendErrored(eq("http://cp:8080"), eq("tp-5"), anyString());
        verify(controlPlaneClient, never()).sendCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("initiateTransfer sends sendStarted then sendCompleted callbacks on successful push transfer")
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

        when(tenantBucketResolver.resolveBucketName(anyString())).thenReturn("provider-bucket");
        when(s3ClientService.generateGetPresignedUrl(eq("provider-bucket"), anyString(), any(Duration.class)))
            .thenReturn(presignedUrl);
        when(s3ClientService.uploadFile(any(), any(), any(), any(), any(ResumableUploadRequest.class)))
            .thenReturn(CompletableFuture.completedFuture("etag-ok"));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(S3Utils.BUCKET_NAME, "consumer-bucket");
        dataAddress.put(S3Utils.ACCESS_KEY, "consumer-access");
        dataAddress.put(S3Utils.SECRET_KEY, "plain-secret");
        dataAddress.put(S3Utils.REGION, "us-east-1");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-1")
                .transferType("HttpData-PUSH")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .datasetId("dataset-1")
                .dataAddress(dataAddress)
                .build();

        protocol.initiateTransfer(dataFlow).join();

        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-1"), anyMap());
        verify(controlPlaneClient).sendCompleted(eq("http://cp:8080"), eq("tp-1"), anyMap());
        verify(controlPlaneClient, never()).sendErrored(any(), any(), any());
    }
}
