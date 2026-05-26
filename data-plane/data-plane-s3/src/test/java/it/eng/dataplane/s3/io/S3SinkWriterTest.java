package it.eng.dataplane.s3.io;

import it.eng.dataplane.api.io.SinkContext;
import it.eng.dataplane.api.io.SinkWriteResult;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link S3SinkWriter}.
 */
@ExtendWith(MockitoExtension.class)
class S3SinkWriterTest {

    @Mock
    private S3ClientService s3ClientService;

    @InjectMocks
    private S3SinkWriter sinkWriter;

    @Test
    @DisplayName("getSinkType returns s3")
    void getSinkType_returnsS3() {
        assertThat(sinkWriter.getSinkType()).isEqualTo("s3");
    }

    @Test
    @DisplayName("write returns a successful result for a valid S3 context")
    void write_withValidContext_returnsSinkWriteResult() {
        InputStream data = new ByteArrayInputStream("payload".getBytes());
        SinkContext context = SinkContext.Builder.newInstance()
                .properties(Map.of(
                        S3Utils.BUCKET_NAME, "bucket-a",
                        S3Utils.OBJECT_KEY, "object-1",
                        S3Utils.ACCESS_KEY, "access-key",
                        S3Utils.SECRET_KEY, "secret-key",
                        S3Utils.ENDPOINT_OVERRIDE, "http://minio:9000",
                        S3Utils.REGION, "us-east-1",
                        "contentType", "text/plain",
                        "contentDisposition", "attachment"))
                .build();

        when(s3ClientService.uploadFile(any(InputStream.class), anyMap(), eq("text/plain"), eq("attachment")))
                .thenReturn(CompletableFuture.completedFuture("etag-123"));

        SinkWriteResult result = sinkWriter.write(data, context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getObjectIdentifier()).isEqualTo("etag-123");
    }

    @Test
    @DisplayName("write returns failure when bucketName is missing")
    void write_withMissingBucketName_returnFailure() {
        InputStream data = new ByteArrayInputStream("payload".getBytes());
        SinkContext context = SinkContext.Builder.newInstance()
                .properties(Map.of(
                        S3Utils.OBJECT_KEY, "object-1",
                        S3Utils.ACCESS_KEY, "access-key",
                        S3Utils.SECRET_KEY, "secret-key",
                        S3Utils.ENDPOINT_OVERRIDE, "http://minio:9000",
                        S3Utils.REGION, "us-east-1"))
                .build();

        SinkWriteResult result = sinkWriter.write(data, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("bucketName");
    }

    @Test
    @DisplayName("write returns failure when upload throws")
    void write_whenUploadThrows_returnsFailure() {
        InputStream data = new ByteArrayInputStream("payload".getBytes());
        SinkContext context = SinkContext.Builder.newInstance()
                .properties(Map.of(
                        S3Utils.BUCKET_NAME, "bucket-a",
                        S3Utils.OBJECT_KEY, "object-1",
                        S3Utils.ACCESS_KEY, "access-key",
                        S3Utils.SECRET_KEY, "secret-key",
                        S3Utils.ENDPOINT_OVERRIDE, "http://minio:9000",
                        S3Utils.REGION, "us-east-1"))
                .build();

        when(s3ClientService.uploadFile(any(InputStream.class), anyMap(), any(), any()))
                .thenThrow(new RuntimeException("upload failed"));

        SinkWriteResult result = sinkWriter.write(data, context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("upload failed");
    }
}
