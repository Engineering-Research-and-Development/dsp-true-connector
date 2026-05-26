package it.eng.dataplane.s3.io;

import it.eng.dataplane.api.io.SourceContext;
import it.eng.dataplane.api.io.SourceOpenResult;
import it.eng.tools.exception.S3ServerException;
import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.BucketCredentialsService;
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
    private S3ClientProvider s3ClientProvider;
    @Mock
    private BucketCredentialsService bucketCredentialsService;
    @Mock
    private S3Properties s3Properties;
    @Mock
    private S3Client s3Client;
    @Mock
    private ResponseInputStream<GetObjectResponse> responseInputStream;
    @Mock
    private GetObjectResponse getObjectResponse;

    @InjectMocks
    private S3SourceReader sourceReader;

    @Test
    @DisplayName("getSourceType returns s3")
    void getSourceType_returnsS3() {
        assertThat(sourceReader.getSourceType()).isEqualTo("s3");
    }

    @Test
    @DisplayName("open returns a finite source result for a valid S3 context")
    void open_withValidContext_returnsOpenResult() {
        SourceContext context = SourceContext.Builder.newInstance()
                .properties(Map.of("bucketName", "bucket-a", "objectKey", "object-1"))
                .build();
        BucketCredentialsEntity credentials = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket-a")
                .accessKey("access-key")
                .secretKey("secret-key")
                .build();

        when(bucketCredentialsService.getBucketCredentials("bucket-a")).thenReturn(credentials);
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3ClientProvider.s3Client(any())).thenReturn(s3Client);
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
                .properties(Map.of("objectKey", "object-1"))
                .build();

        assertThatThrownBy(() -> sourceReader.open(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bucketName");
    }

    @Test
    @DisplayName("open throws when objectKey is missing")
    void open_withMissingObjectKey_throwsException() {
        SourceContext context = SourceContext.Builder.newInstance()
                .properties(Map.of("bucketName", "bucket-a"))
                .build();

        assertThatThrownBy(() -> sourceReader.open(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectKey");
    }

    @Test
    @DisplayName("open returns failure result when getBucketCredentials throws S3ServerException")
    void open_whenBucketCredentialsThrowsS3ServerException_returnsFailureResult() {
        SourceContext context = SourceContext.Builder.newInstance()
                .properties(Map.of("bucketName", "bucket-a", "objectKey", "object-1"))
                .build();

        when(bucketCredentialsService.getBucketCredentials("bucket-a"))
                .thenThrow(new S3ServerException("Bucket credentials not found for bucket: bucket-a"));

        SourceOpenResult result = sourceReader.open(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("bucket-a").contains("object-1");
    }

    @Test
    @DisplayName("open returns failure result when S3 SDK throws an exception")
    void open_whenSdkThrows_returnsFailureResult() {
        SourceContext context = SourceContext.Builder.newInstance()
                .properties(Map.of("bucketName", "bucket-a", "objectKey", "object-1"))
                .build();
        BucketCredentialsEntity credentials = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket-a")
                .accessKey("access-key")
                .secretKey("secret-key")
                .build();

        when(bucketCredentialsService.getBucketCredentials("bucket-a")).thenReturn(credentials);
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getEndpoint()).thenReturn("http://minio:9000");
        when(s3ClientProvider.s3Client(any())).thenReturn(s3Client);
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(SdkException.create("connection refused", null));

        SourceOpenResult result = sourceReader.open(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("bucket-a").contains("object-1");
    }
}
