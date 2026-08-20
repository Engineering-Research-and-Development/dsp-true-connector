package it.eng.tools.s3.service;

import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.model.S3ClientRequest;
import it.eng.tools.s3.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BucketConnectionVerificationServiceTest {

    private static final String BUCKET_NAME = "my-bucket";
    private static final String ACCESS_KEY = "candidate-access-key";
    private static final String SECRET_KEY = "candidate-secret-key";

    @Mock
    private S3ClientProvider s3ClientProvider;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private S3Client s3Client;

    private BucketConnectionVerificationService service;

    @BeforeEach
    void setUp() {
        service = new BucketConnectionVerificationService(s3ClientProvider, s3Properties);
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3Properties.getExternalPresignedEndpoint()).thenReturn("http://minio:9000");
        when(s3ClientProvider.s3Client(any(S3ClientRequest.class))).thenReturn(s3Client);
    }

    @Test
    @DisplayName("Returns true when headBucket succeeds")
    void returnsTrueWhenHeadBucketSucceeds() {
        boolean result = service.verify(BUCKET_NAME, ACCESS_KEY, SECRET_KEY);

        assertTrue(result);
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3ClientProvider).clearBucketCache(BUCKET_NAME);
    }

    @Test
    @DisplayName("Returns false when the bucket does not exist")
    void returnsFalseWhenBucketDoesNotExist() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("no such bucket").build());

        boolean result = service.verify(BUCKET_NAME, ACCESS_KEY, SECRET_KEY);

        assertFalse(result);
        verify(s3ClientProvider).clearBucketCache(BUCKET_NAME);
    }

    @Test
    @DisplayName("Returns false when access is denied or credentials are rejected")
    void returnsFalseWhenAccessDenied() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder().message("access denied").statusCode(403).build());

        boolean result = service.verify(BUCKET_NAME, ACCESS_KEY, SECRET_KEY);

        assertFalse(result);
        verify(s3ClientProvider).clearBucketCache(BUCKET_NAME);
    }
}
