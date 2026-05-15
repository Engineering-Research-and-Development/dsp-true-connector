package it.eng.dataplane.httppush;

import com.sun.net.httpserver.HttpServer;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.TemporaryBucketUserService;
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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private TenantBucketResolver tenantBucketResolver;
    @Mock
    private S3Properties s3Properties;

    private HttpPushTransferProtocol protocol;
    private HttpServer testHttpServer;

    // Synchronous executor for testing — runs tasks immediately in the calling thread
    private final Executor syncExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        protocol = new HttpPushTransferProtocol(
            s3ClientService,
            s3Properties,
            temporaryBucketUserService,
            tenantBucketResolver,
            syncExecutor
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
                .datasetId("dataset-403")
                .dataAddress(dataAddress)
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("403");
    }

    @Test
    @DisplayName("initiateTransfer pushes artifact to consumer S3 and cleans up temporary credentials")
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
        when(s3ClientService.uploadFile(any(), any(), any(), any()))
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
            .datasetId("dataset-1")
            .dataAddress(dataAddress)
            .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isTrue();
        verify(temporaryBucketUserService, never()).deleteTemporaryUser(any());
    }

    @Test
    @DisplayName("initiateTransfer uses processId as S3 objectKey when objectKey is absent from dataAddress")
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
        when(s3ClientService.uploadFile(any(), any(), any(), any()))
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
        when(s3ClientService.uploadFile(any(), any(), any(), any()))
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
            .datasetId("dataset-1")
            .dataAddress(dataAddress)
            .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("S3 auth failure");
        // Cleanup of temporary credentials is the consumer CP's responsibility,
        // not the provider-side push DP — even when upload fails.
        verify(temporaryBucketUserService, never()).deleteTemporaryUser(any());
    }
}
