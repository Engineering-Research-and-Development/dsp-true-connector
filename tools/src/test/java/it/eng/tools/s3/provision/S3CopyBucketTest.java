package it.eng.tools.s3.provision;

import it.eng.tools.s3.configuration.AwsClientProvider;
import it.eng.tools.s3.provision.model.S3CopyBucketDefinition;
import it.eng.tools.s3.provision.model.S3CopyBucketResponse;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.internal.multipart.MultipartS3AsyncClient;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.multipart.MultipartConfiguration;

import java.util.concurrent.ExecutionException;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3CopyBucketTest {

    @InjectMocks
    private S3CopyBucket s3CopyBucket;
    @Mock
    private AwsClientProvider awsClientProvider;
    @Mock
    private MultipartConfiguration multipartConfiguration;

    @Mock
    private S3AsyncClient s3Client;
    @Mock
    private MultipartS3AsyncClient multipartS3Client;
    @Mock
    private SecretToken secretToken;

    @BeforeEach
    void setUp() {
    }

    @Test
    void copyBucket() throws ExecutionException, InterruptedException {
        S3CopyBucketDefinition copyBucketDefinition = getS3CopyBucketDefinition();

        when(awsClientProvider.s3AsyncClient(any(S3ClientRequest.class))).thenReturn(s3Client);
        var copyObjectResponse = CopyObjectResponse.builder().build();
        when(multipartS3Client.copyObject(any(CopyObjectRequest.class))).thenReturn(completedFuture(copyObjectResponse));

        try (var s3MultipartClient = mockStatic(MultipartS3AsyncClient.class)) {
            s3MultipartClient
                    .when(() -> MultipartS3AsyncClient.create(eq(s3Client), any(), eq(true)))
                    .thenReturn(multipartS3Client);

            S3CopyBucketResponse<Object> copyBucketResponse = s3CopyBucket.copyBucket(copyBucketDefinition).get();

            assertTrue(copyBucketResponse.succeeded());
//            verify(multipartS3Client).copyObject(argThat((CopyObjectRequest copyRequest) -> copyRequest.sourceBucket().equals(sourceBucket) &&
//                    copyRequest.sourceKey().equals(sourceObject) &&
//                    copyRequest.destinationBucket().equals(destinationBucket) &&
//                    copyRequest.destinationKey().equals(destinationObject))
//            );
        }
    }

    @Test
    public void copyBucket_fail_multipartS3Client() throws ExecutionException, InterruptedException {
        S3CopyBucketDefinition copyBucketDefinition = getS3CopyBucketDefinition();

        when(awsClientProvider.s3AsyncClient(any(S3ClientRequest.class))).thenReturn(s3Client);
        var copyObjectResponse = CopyObjectResponse.builder().build();
        when(multipartS3Client.copyObject(any(CopyObjectRequest.class))).thenReturn(failedFuture(NoSuchBucketException.builder()
                .message("No such bucket")
                .statusCode(404)
                .build()));

        try (var s3MultipartClient = mockStatic(MultipartS3AsyncClient.class)) {
            s3MultipartClient
                    .when(() -> MultipartS3AsyncClient.create(eq(s3Client), any(), eq(true)))
                    .thenReturn(multipartS3Client);

            S3CopyBucketResponse<Object> copyBucketResponse = s3CopyBucket.copyBucket(copyBucketDefinition).get();

            assertFalse(copyBucketResponse.succeeded());
        }
    }

    @NotNull
    private S3CopyBucketDefinition getS3CopyBucketDefinition() {
        return S3CopyBucketDefinition.Builder.newInstance()
                .sourceBucket("source-bucket")
                .sourceKey("source-key")
                .destinationBucket("destination-bucket")
                .destinationKey("destination-key")
                .endpointOverride("http://endpoint.override")
                .destinationRegion("eu-west-1")
                .secretToken(secretToken)
                .build();
    }
}
