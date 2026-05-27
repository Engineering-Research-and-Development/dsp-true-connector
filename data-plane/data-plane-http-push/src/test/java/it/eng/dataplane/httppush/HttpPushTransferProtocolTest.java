package it.eng.dataplane.httppush;

import com.sun.net.httpserver.HttpServer;
import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.s3.util.S3Utils;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private S3Properties s3Properties;
    @Mock
    private ControlPlaneClient controlPlaneClient;

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
    @DisplayName("getProtocolId returns HttpData-PUSH")
    void getProtocolId_returnsHttpDataPush() {
        assertThat(protocol.getProtocolId()).isEqualTo("HttpData-PUSH");
    }

    @Test
    @DisplayName("prepare uses sink metadata view mode to return a presigned URL for the stored processId object")
    void prepareViewModeUsesSinkMetadata() {
        when(s3Properties.getBucketName()).thenReturn("consumer-bucket");
        when(s3ClientService.generateGetPresignedUrl("consumer-bucket", "tp-view", Duration.ofDays(7L)))
                .thenReturn("https://example.com/pushed");

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-view")
                .datasetId("dataset-1")
                .metadata(Map.of("sink", Map.of("mode", "VIEW")))
                .build();

        DataFlowPrepareResponse response = protocol.prepare(message);

        assertThat(response.getDataAddress()).containsEntry("presignedUrl", "https://example.com/pushed");
        verify(s3ClientService).generateGetPresignedUrl("consumer-bucket", "tp-view", Duration.ofDays(7L));
    }

    @Test
    @DisplayName("initiateTransfer returns failure when dataAddress is empty (no sink.bucketName)")
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
    @DisplayName("suspendTransfer returns failure with 'not supported' message")
    void suspendTransfer_returnsNotSupported() throws Exception {
        DataFlowResult result = protocol.suspendTransfer("df-1").get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("not supported");
    }

    @Test
    @DisplayName("resumeTransfer returns failure with 'not supported' message")
    void resumeTransfer_returnsNotSupported() throws Exception {
        DataFlowResult result = protocol.resumeTransfer("df-1").get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("not supported");
    }

    @Test
    @DisplayName("terminateTransfer returns success without throwing")
    void terminateTransfer_returnsSuccess() throws Exception {
        DataFlowResult result = protocol.terminateTransfer("df-1").get();

        assertThat(result.isSuccess()).isTrue();
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

        when(s3ClientService.generateGetPresignedUrl(eq("provider-bucket"), anyString(), any(Duration.class)))
            .thenReturn(presignedUrl);

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME, "provider-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY, "dataset-403");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "consumer-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "consumer-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "plain-secret");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "us-east-1");

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
    @DisplayName("initiateTransfer pushes artifact to consumer S3 using CP-provided source and sink properties")
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

        when(s3ClientService.generateGetPresignedUrl(eq("provider-bucket"), anyString(), any(Duration.class)))
            .thenReturn(presignedUrl);
        when(s3ClientService.uploadFile(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture("etag-xyz"));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME, "provider-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY, "dataset-1");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "consumer-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY, "tp-obj-key");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "consumer-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "plain-secret");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE, "http://consumer-minio:9000");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "us-east-1");

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
    @DisplayName("initiateTransfer uses CP-provided source properties for provider presigned URL (no DP-local bucket resolution)")
    void initiateTransfer_usesCPProvidedSourcePropertiesForPresignedUrl() throws Exception {
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

        when(s3ClientService.generateGetPresignedUrl("cp-provider-bucket", "dataset-1", Duration.ofDays(1L)))
                .thenReturn(presignedUrl);
        when(s3ClientService.uploadFile(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture("etag-push"));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME, "cp-provider-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY, "dataset-1");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "cp-consumer-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY, "tp-obj");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "consumer-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "consumer-secret");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "eu-west-1");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-source-test")
                .transferType("HttpData-PUSH")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .datasetId("dataset-1")
                .dataAddress(dataAddress)
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isTrue();
        // Verify CP-provided source bucket was used for presigned URL generation
        verify(s3ClientService).generateGetPresignedUrl("cp-provider-bucket", "dataset-1", Duration.ofDays(1L));
        // Verify consumer sink properties were used for upload
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> s3PropsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(s3ClientService).uploadFile(any(), s3PropsCaptor.capture(), any(), any());
        assertThat(s3PropsCaptor.getValue())
                .containsEntry(S3Utils.BUCKET_NAME, "cp-consumer-bucket")
                .containsEntry(S3Utils.OBJECT_KEY, "tp-obj")
                .containsEntry(S3Utils.ACCESS_KEY, "consumer-access")
                .containsEntry(S3Utils.SECRET_KEY, "consumer-secret");
    }

    @Test
    @DisplayName("initiateTransfer sends started/completed callbacks and uses sink.objectKey from dataAddress; falls back to processId when absent")
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

        when(s3ClientService.generateGetPresignedUrl(anyString(), anyString(), any(Duration.class)))
            .thenReturn(presignedUrl);
        when(s3ClientService.uploadFile(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture("etag-xyz"));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME, "provider-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY, "dataset-1");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "consumer-bucket");
        // intentionally omit SINK_OBJECT_KEY to test fallback to processId
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "consumer-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "plain-secret");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "us-east-1");

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
        verify(s3ClientService).uploadFile(any(), s3PropsCaptor.capture(), any(), any());
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

        when(s3ClientService.generateGetPresignedUrl(anyString(), anyString(), any(Duration.class)))
            .thenReturn(presignedUrl);
        // Simulate upload failure
        when(s3ClientService.uploadFile(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("S3 auth failure")));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME, "provider-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY, "dataset-1");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "consumer-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "consumer-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "plain-secret");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "us-east-1");

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

        when(s3ClientService.generateGetPresignedUrl(eq("provider-bucket"), anyString(), any(Duration.class)))
            .thenReturn(presignedUrl);
        when(s3ClientService.uploadFile(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture("etag-ok"));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME, "provider-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY, "dataset-1");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "consumer-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "consumer-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "plain-secret");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "us-east-1");

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
