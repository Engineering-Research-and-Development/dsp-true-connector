package it.eng.dataplane.httppush;

import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.io.SourceContext;
import it.eng.dataplane.api.io.SourceOpenResult;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.s3.io.S3SourceReader;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.model.TemporaryBucketUser;
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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    @DisplayName("prepare uses sink.s3 management credentials when creating temporary upload credentials")
    void prepare_usesSinkS3ManagementCredentialsForTemporaryUserCreation() {
        TemporaryBucketUser tempUser = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId("tp-prepare")
                .bucketName("tenant-bucket")
                .objectKey("tp-prepare")
                .accessKey("temp-access")
                .secretKey("temp-secret")
                .build();
        when(temporaryBucketUserService.createTemporaryUser(
                eq("tp-prepare"),
                any(BucketCredentialsEntity.class),
                eq("tenant-bucket"),
                eq("tp-prepare")))
                .thenReturn(tempUser);

        DataFlowPrepareMessage prepareMessage = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-prepare")
                .transferType("HttpData-PUSH")
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "tenant-bucket",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "cp-minioadmin",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "cp-minioadmin-secret",
                                        DataPlaneConstants.METADATA_S3_REGION, "us-east-1",
                                        DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, "http://minio:9000"))))
                .build();

        DataFlowPrepareResponse response = protocol.prepare(prepareMessage);

        assertThat(response.getDataAddress())
                .containsEntry(S3Utils.BUCKET_NAME, "tenant-bucket")
                .containsEntry(S3Utils.OBJECT_KEY, "tp-prepare")
                .containsEntry(S3Utils.REGION, "us-east-1")
                .containsEntry(S3Utils.ACCESS_KEY, "temp-access")
                .containsEntry(S3Utils.SECRET_KEY, "temp-secret")
                .containsEntry(S3Utils.ENDPOINT_OVERRIDE, "http://minio:9000");
        verify(temporaryBucketUserService).createTemporaryUser(
                eq("tp-prepare"),
                argThat(credentials -> "cp-minioadmin".equals(credentials.getAccessKey())
                        && "cp-minioadmin-secret".equals(credentials.getSecretKey())
                        && "tenant-bucket".equals(credentials.getBucketName())),
                eq("tenant-bucket"),
                eq("tp-prepare"));
    }

    @Test
    @DisplayName("prepare reads endpointOverride from metadata.sink.s3 — not from local s3.endpoint or s3.externalPresignedEndpoint")
    void prepare_usesSinkInternalEndpointNotExternalPresignedEndpoint() {
        TemporaryBucketUser tempUser = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId("tp-internal")
                .bucketName("tenant-bucket")
                .objectKey("tp-internal")
                .accessKey("temp-access")
                .secretKey("temp-secret")
                .build();
        when(temporaryBucketUserService.createTemporaryUser(
                eq("tp-internal"),
                any(BucketCredentialsEntity.class),
                eq("tenant-bucket"),
                eq("tp-internal")))
                .thenReturn(tempUser);

        DataFlowPrepareMessage prepareMessage = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-internal")
                .transferType("HttpData-PUSH")
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "tenant-bucket",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "cp-minioadmin",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "cp-minioadmin-secret",
                                        DataPlaneConstants.METADATA_S3_REGION, "us-east-1",
                                        DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, "http://minio:9000"))))
                .build();

        DataFlowPrepareResponse response = protocol.prepare(prepareMessage);

        // Server-side upload must use the internal/container-reachable endpoint from metadata
        assertThat(response.getDataAddress())
                .containsEntry(S3Utils.ENDPOINT_OVERRIDE, "http://minio:9000")
                .doesNotContainEntry(S3Utils.ENDPOINT_OVERRIDE, "http://172.17.0.1:9000");
    }

    @Test
    @DisplayName("prepare view mode uses sink.s3 bucket metadata when generating presigned URL")
    void prepare_viewMode_usesSinkS3BucketMetadataForPresignedUrl() {
        Map<String, String> expectedSinkProperties = Map.of(
                S3Utils.BUCKET_NAME, "tenant-bucket",
                S3Utils.OBJECT_KEY, "tp-view",
                S3Utils.ACCESS_KEY, "bucket-access",
                S3Utils.SECRET_KEY, "bucket-secret",
                S3Utils.REGION, "us-east-1",
                S3Utils.ENDPOINT_OVERRIDE, "http://172.17.0.1:9000");
        when(s3ClientService.generateGetPresignedUrl(expectedSinkProperties, Duration.ofDays(7L)))
                .thenReturn("http://presigned/view");

        DataFlowPrepareMessage prepareMessage = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-view")
                .transferType("HttpData-PUSH")
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_FIELD_MODE, "VIEW",
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "tenant-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "tp-view",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "bucket-access",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "bucket-secret",
                                        DataPlaneConstants.METADATA_S3_REGION, "us-east-1",
                                        DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, "http://172.17.0.1:9000"))))
                .build();

        DataFlowPrepareResponse response = protocol.prepare(prepareMessage);

        assertThat(response.getDataAddress()).containsEntry(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY, "http://presigned/view");
        verify(s3ClientService).generateGetPresignedUrl(expectedSinkProperties, Duration.ofDays(7L));
        verify(temporaryBucketUserService, never()).createTemporaryUser(anyString(), any(BucketCredentialsEntity.class),
                anyString(), anyString());
    }

    @Test
    @DisplayName("initiateTransfer returns failure when dataAddress is null")
    void initiateTransfer_failsWhenDataAddressNull() throws Exception {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-null")
                .transferType("HttpData-PUSH")
                .dataAddress(null)
                // no metadata.sink.s3 present — triggers bucketName validation
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("metadata.sink.s3.bucketName");
        verify(controlPlaneClient, never()).sendStarted(any(), any(), any());
    }

    @Test
    @DisplayName("initiateTransfer returns failure when metadata.sink.s3.bucketName is missing")
    void initiateTransfer_failsWhenSinkBucketNameMissing() throws Exception {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-missing-sink")
                .transferType("HttpData-PUSH")
                .metadata(Map.of(DataPlaneConstants.METADATA_SECTION_SOURCE, Map.of(
                        DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                DataPlaneConstants.METADATA_S3_BUCKET_NAME, "provider-bucket"))))
                // no metadata.sink.s3 — should fail
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("metadata.sink.s3.bucketName");
        verify(controlPlaneClient, never()).sendStarted(any(), any(), any());
    }

    @Test
    @DisplayName("initiateTransfer returns failure when metadata.source.s3.bucketName is missing")
    void initiateTransfer_failsWhenSourceBucketNameMissing() throws Exception {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-missing-source")
                .transferType("HttpData-PUSH")
                .metadata(Map.of(DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                        DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket"))))
                // no metadata.source.s3 — should fail
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("metadata.source.s3.bucketName");
        verify(controlPlaneClient, never()).sendStarted(any(), any(), any());
    }

    @Test
    @DisplayName("initiateTransfer converts IllegalArgumentException from source-open into failure and sends sendErrored callback")
    void initiateTransfer_convertsSourceOpenIllegalArgumentExceptionToFailure() throws Exception {
        when(s3SourceReader.open(any(SourceContext.class)))
            .thenThrow(new IllegalArgumentException("region is required"));

        // source.s3 has no region — triggers IllegalArgumentException from s3SourceReader
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-illegal-arg")
                .transferType("HttpData-PUSH")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .datasetId("dataset-1")
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SOURCE, Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "provider-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "dataset-1",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "src-access",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "src-secret")),
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "consumer-access",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "plain-secret",
                                        DataPlaneConstants.METADATA_S3_REGION, "us-east-1"))))
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

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-403")
                .transferType("HttpData-PUSH")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .datasetId("dataset-403")
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SOURCE, Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "provider-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "dataset-403",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "src-access",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "src-secret",
                                        DataPlaneConstants.METADATA_S3_REGION, "us-east-1")),
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "consumer-access",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "plain-secret",
                                        DataPlaneConstants.METADATA_S3_REGION, "us-east-1"))))
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("access denied on source object");
        verify(controlPlaneClient).sendStarted(eq("http://cp:8080"), eq("tp-403"), anyMap());
        verify(controlPlaneClient).sendErrored(eq("http://cp:8080"), eq("tp-403"), anyString());
    }

    @Test
    @DisplayName("initiateTransfer pushes artifact to consumer S3 using CP-provided source and sink metadata")
    void initiateTransfer_successfulPushToConsumerS3() throws Exception {
        when(s3SourceReader.open(any(SourceContext.class)))
            .thenReturn(SourceOpenResult.success(
                new ByteArrayInputStream("artifact-content".getBytes()),
                "application/octet-stream", 16L, true));
        when(s3ClientService.uploadFile(any(), any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture("etag-xyz"));

        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .processId("tp-3")
            .transferType("HttpData-PUSH")
            .tenantId("tenant-1")
            .callbackAddress("http://cp:8080")
            .datasetId("dataset-1")
            .metadata(Map.of(
                    DataPlaneConstants.METADATA_SECTION_SOURCE, Map.of(
                            DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                    DataPlaneConstants.METADATA_S3_BUCKET_NAME, "provider-bucket",
                                    DataPlaneConstants.METADATA_S3_OBJECT_KEY, "dataset-1",
                                    DataPlaneConstants.METADATA_S3_ACCESS_KEY, "src-access",
                                    DataPlaneConstants.METADATA_S3_SECRET_KEY, "src-secret",
                                    DataPlaneConstants.METADATA_S3_REGION, "us-east-1")),
                    DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                            DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                    DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket",
                                    DataPlaneConstants.METADATA_S3_OBJECT_KEY, "tp-obj-key",
                                    DataPlaneConstants.METADATA_S3_ACCESS_KEY, "consumer-access",
                                    DataPlaneConstants.METADATA_S3_SECRET_KEY, "plain-secret",
                                    DataPlaneConstants.METADATA_S3_REGION, "us-east-1"))))
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

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .processId("tp-sync-upload-failure")
                .transferType("HttpData-PUSH")
                .tenantId("tenant-1")
                .callbackAddress("http://cp:8080")
                .datasetId("dataset-1")
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SOURCE, Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "provider-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "dataset-1",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "src-access",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "src-secret",
                                        DataPlaneConstants.METADATA_S3_REGION, "us-east-1")),
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "tp-obj-key",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "consumer-access",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "plain-secret",
                                        DataPlaneConstants.METADATA_S3_REGION, "us-east-1"))))
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
    @DisplayName("completeTransfer deletes temporary bucket user to release credentials after a successful push")
    void completeTransfer_deletesTemporaryUser() throws Exception {
        DataFlowResult result = protocol.completeTransfer("tp-push-complete").get();

        assertThat(result.isSuccess()).isTrue();
        verify(temporaryBucketUserService).deleteTemporaryUser("tp-push-complete");
    }

    @Test
    @DisplayName("completeTransfer returns success even when temp user deletion fails (best-effort cleanup)")
    void completeTransfer_returnsSucessEvenWhenDeletionFails() throws Exception {
        doThrow(new RuntimeException("IAM user not found")).when(temporaryBucketUserService)
                .deleteTemporaryUser("tp-push-missing");

        DataFlowResult result = protocol.completeTransfer("tp-push-missing").get();

        assertThat(result.isSuccess()).isTrue();
        verify(temporaryBucketUserService).deleteTemporaryUser("tp-push-missing");
    }

    @Test
    @DisplayName("terminateTransfer returns success without throwing")
    void terminateTransfer_returnsSuccess() throws Exception {
        DataFlowResult result = protocol.terminateTransfer("df-1").get();

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("terminateTransfer deletes temporary bucket user to prevent credential leak on prepare/request failure")
    void terminateTransfer_deletesTemporaryBucketUser() throws Exception {
        DataFlowResult result = protocol.terminateTransfer("tp-cleanup").get();

        assertThat(result.isSuccess()).isTrue();
        verify(temporaryBucketUserService).deleteTemporaryUser("tp-cleanup");
    }

    @Test
    @DisplayName("terminateTransfer returns success even when temp user deletion fails (best-effort cleanup)")
    void terminateTransfer_returnsSucessEvenWhenDeletionFails() throws Exception {
        doThrow(new RuntimeException("IAM user not found")).when(temporaryBucketUserService).deleteTemporaryUser("tp-missing");

        DataFlowResult result = protocol.terminateTransfer("tp-missing").get();

        assertThat(result.isSuccess()).isTrue();
        verify(temporaryBucketUserService).deleteTemporaryUser("tp-missing");
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
