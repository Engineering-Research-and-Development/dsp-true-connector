package it.eng.dataplane.httppush;

import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.io.SourceContext;
import it.eng.dataplane.api.io.SourceOpenResult;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.s3.io.S3SourceReader;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.s3.util.S3Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private S3SourceReader s3SourceReader;
    @Mock
    private ControlPlaneClient controlPlaneClient;

    private HttpPushTransferProtocol protocol;

    // Synchronous executor for testing — runs tasks immediately in the calling thread
    private final Executor syncExecutor = Runnable::run;

    @BeforeEach
    void setUp() {
        protocol = new HttpPushTransferProtocol(
            s3ClientService,
            s3Properties,
            temporaryBucketUserService,
            s3SourceReader,
            syncExecutor,
            controlPlaneClient
        );
    }

    @Test
    @DisplayName("getProtocolId returns HttpData-PUSH")
    void getProtocolId_returnsHttpDataPush() {
        assertThat(protocol.getProtocolId()).isEqualTo("HttpData-PUSH");
    }

    @Test
    @DisplayName("initiateTransfer returns failure when dataAddress is null")
    void initiateTransfer_failsWhenDataAddressNull() throws Exception {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-null")
                .transferType("HttpData-PUSH")
                .dataAddress(null)
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("sink.bucketName");
        verify(controlPlaneClient, never()).sendStarted(any(), any(), any());
    }

    @Test
    @DisplayName("initiateTransfer returns failure when sink.bucketName is missing")
    void initiateTransfer_failsWhenSinkBucketNameMissing() throws Exception {
        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME, "provider-bucket");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-missing-sink")
                .transferType("HttpData-PUSH")
                .dataAddress(dataAddress)
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("sink.bucketName");
        verify(controlPlaneClient, never()).sendStarted(any(), any(), any());
    }

    @Test
    @DisplayName("initiateTransfer returns failure when source.bucketName is missing")
    void initiateTransfer_failsWhenSourceBucketNameMissing() throws Exception {
        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "consumer-bucket");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-missing-source")
                .transferType("HttpData-PUSH")
                .dataAddress(dataAddress)
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("source.bucketName");
        verify(controlPlaneClient, never()).sendStarted(any(), any(), any());
    }

    @Test
    @DisplayName("initiateTransfer converts IllegalArgumentException from source-open into failure and sends sendErrored callback")
    void initiateTransfer_convertsSourceOpenIllegalArgumentExceptionToFailure() throws Exception {
        when(s3SourceReader.open(any(SourceContext.class)))
            .thenThrow(new IllegalArgumentException("region is required"));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME, "provider-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY, "dataset-1");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_ACCESS_KEY, "src-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_SECRET_KEY, "src-secret");
        // Missing SOURCE_REGION to trigger IllegalArgumentException
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "consumer-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "consumer-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "plain-secret");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "us-east-1");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-illegal-arg")
                .transferType("HttpData-PUSH")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .datasetId("dataset-1")
                .dataAddress(dataAddress)
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("region is required");
        // Verify CP received sendStarted followed by sendErrored
        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-illegal-arg"), anyMap());
        verify(controlPlaneClient).sendErrored(eq("http://cp:8080"), eq("tp-illegal-arg"), anyString());
    }

    @Test
    @DisplayName("initiateTransfer returns failure when S3 source open fails")
    void initiateTransfer_returnsFailureWhenSourceOpenFails() throws Exception {
        when(s3SourceReader.open(any(SourceContext.class)))
            .thenReturn(SourceOpenResult.failure("S3 error: access denied on source object"));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME, "provider-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY, "dataset-403");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_ACCESS_KEY, "src-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_SECRET_KEY, "src-secret");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_REGION, "us-east-1");
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
        assertThat(result.getErrorMessage()).contains("access denied on source object");
        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-403"), anyMap());
        verify(controlPlaneClient).sendErrored(eq("http://cp:8080"), eq("tp-403"), anyString());
    }

    @Test
    @DisplayName("initiateTransfer pushes artifact to consumer S3 using CP-provided source and sink properties")
    void initiateTransfer_successfulPushToConsumerS3() throws Exception {
        when(s3SourceReader.open(any(SourceContext.class)))
            .thenReturn(SourceOpenResult.success(
                new ByteArrayInputStream("artifact-content".getBytes()),
                "application/octet-stream", 16L, true));
        when(s3ClientService.uploadFile(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture("etag-xyz"));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME, "provider-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY, "dataset-1");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_ACCESS_KEY, "src-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_SECRET_KEY, "src-secret");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_REGION, "us-east-1");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "consumer-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY, "tp-obj-key");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "consumer-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "plain-secret");
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
    }

    @Test
    @DisplayName("initiateTransfer closes provider source stream when uploadFile throws synchronously")
    void initiateTransfer_closesProviderStreamWhenUploadThrowsSynchronously() throws Exception {
        CloseTrackingInputStream artifactStream = new CloseTrackingInputStream("artifact-content".getBytes());
        when(s3SourceReader.open(any(SourceContext.class)))
                .thenReturn(SourceOpenResult.success(artifactStream,
                        "application/octet-stream", 16L, true));
        when(s3ClientService.uploadFile(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("sync upload failure"));

        Map<String, String> dataAddress = new HashMap<>();
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME, "provider-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY, "dataset-1");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_ACCESS_KEY, "src-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_SECRET_KEY, "src-secret");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_REGION, "us-east-1");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME, "consumer-bucket");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY, "tp-obj-key");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY, "consumer-access");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY, "plain-secret");
        dataAddress.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION, "us-east-1");

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-sync-upload-failure")
                .transferType("HttpData-PUSH")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .datasetId("dataset-1")
                .dataAddress(dataAddress)
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("sync upload failure");
        assertThat(artifactStream.isClosed()).isTrue();
        verify(controlPlaneClient).sendErrored(eq("http://cp:8080"), eq("tp-sync-upload-failure"), anyString());
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

    private static final class CloseTrackingInputStream extends InputStream {

        private final ByteArrayInputStream delegate;
        private boolean closed;

        private CloseTrackingInputStream(byte[] payload) {
            this.delegate = new ByteArrayInputStream(payload);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }

        private boolean isClosed() {
            return closed;
        }
    }
}
