package it.eng.dataplane.s3.io;

import it.eng.dataplane.api.io.SourceContext;
import it.eng.dataplane.api.io.SourceOpenResult;
import it.eng.tools.s3.configuration.S3ClientFactory;
import it.eng.tools.s3.util.S3Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link S3SourceReader}.
 */
@ExtendWith(MockitoExtension.class)
class S3SourceReaderTest {

    @Mock
    private S3ClientFactory s3ClientFactory;
    @Mock
    private S3Client s3Client;
    @Mock
    private ResponseInputStream<GetObjectResponse> responseInputStream;
    @Mock
    private GetObjectResponse getObjectResponse;

    @InjectMocks
    private S3SourceReader sourceReader;

    private static Map<String, String> fullProperties() {
        return Map.of(
                S3Utils.BUCKET_NAME, "bucket-a",
                S3Utils.OBJECT_KEY, "object-1",
                S3Utils.ACCESS_KEY, "access-key",
                S3Utils.SECRET_KEY, "secret-key",
                S3Utils.REGION, "us-east-1",
                S3Utils.ENDPOINT_OVERRIDE, "http://minio:9000"
        );
    }

    @Test
    @DisplayName("getSourceType returns s3")
    void getSourceType_returnsS3() {
        assertThat(sourceReader.getSourceType()).isEqualTo("s3");
    }

    @Test
    @DisplayName("open returns a finite source result for a valid S3 context")
    void open_withValidContext_returnsOpenResult() {
        SourceContext context = SourceContext.Builder.newInstance()
                .properties(fullProperties())
                .build();

        when(s3ClientFactory.getClient(any())).thenReturn(s3Client);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);
        when(responseInputStream.response()).thenReturn(getObjectResponse);
        when(getObjectResponse.contentType()).thenReturn("text/plain");
        when(getObjectResponse.contentLength()).thenReturn(42L);

        SourceOpenResult result = sourceReader.open(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isFinite()).isTrue();
        assertThat(result.getStream()).isSameAs(responseInputStream);
        assertThat(result.getContentType()).isEqualTo("text/plain");
        assertThat(result.getContentLength()).isEqualTo(42L);
    }

    @Test
    @DisplayName("open throws when bucketName is missing")
    void open_withMissingBucketName_throwsException() {
        SourceContext context = SourceContext.Builder.newInstance()
                .properties(Map.of(
                        S3Utils.OBJECT_KEY, "object-1",
                        S3Utils.ACCESS_KEY, "access-key",
                        S3Utils.SECRET_KEY, "secret-key",
                        S3Utils.REGION, "us-east-1"))
                .build();

        assertThatThrownBy(() -> sourceReader.open(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucketName");
    }

    @Test
    @DisplayName("open throws when objectKey is missing")
    void open_withMissingObjectKey_throwsException() {
        SourceContext context = SourceContext.Builder.newInstance()
                .properties(Map.of(
                        S3Utils.BUCKET_NAME, "bucket-a",
                        S3Utils.ACCESS_KEY, "access-key",
                        S3Utils.SECRET_KEY, "secret-key",
                        S3Utils.REGION, "us-east-1"))
                .build();

        assertThatThrownBy(() -> sourceReader.open(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectKey");
    }

    @Test
    @DisplayName("open throws when accessKey is missing")
    void open_withMissingAccessKey_throwsException() {
        SourceContext context = SourceContext.Builder.newInstance()
                .properties(Map.of(
                        S3Utils.BUCKET_NAME, "bucket-a",
                        S3Utils.OBJECT_KEY, "object-1",
                        S3Utils.SECRET_KEY, "secret-key",
                        S3Utils.REGION, "us-east-1"))
                .build();

        assertThatThrownBy(() -> sourceReader.open(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessKey");
    }

    @Test
    @DisplayName("open throws when secretKey is missing")
    void open_withMissingSecretKey_throwsException() {
        SourceContext context = SourceContext.Builder.newInstance()
                .properties(Map.of(
                        S3Utils.BUCKET_NAME, "bucket-a",
                        S3Utils.OBJECT_KEY, "object-1",
                        S3Utils.ACCESS_KEY, "access-key",
                        S3Utils.REGION, "us-east-1"))
                .build();

        assertThatThrownBy(() -> sourceReader.open(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secretKey");
    }

    @Test
    @DisplayName("open throws when region is missing")
    void open_withMissingRegion_throwsException() {
        SourceContext context = SourceContext.Builder.newInstance()
                .properties(Map.of(
                        S3Utils.BUCKET_NAME, "bucket-a",
                        S3Utils.OBJECT_KEY, "object-1",
                        S3Utils.ACCESS_KEY, "access-key",
                        S3Utils.SECRET_KEY, "secret-key"))
                .build();

        assertThatThrownBy(() -> sourceReader.open(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region");
    }

    @Test
    @DisplayName("open returns failure result when S3 SDK throws an exception")
    void open_whenSdkThrows_returnsFailureResult() {
        SourceContext context = SourceContext.Builder.newInstance()
                .properties(fullProperties())
                .build();

        when(s3ClientFactory.getClient(any())).thenReturn(s3Client);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(SdkException.create("connection refused", null));

        SourceOpenResult result = sourceReader.open(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("bucket-a").contains("object-1");
    }
}
