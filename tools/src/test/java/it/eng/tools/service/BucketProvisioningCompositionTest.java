package it.eng.tools.service;

import it.eng.tools.model.BucketProvisioningMode;
import it.eng.tools.model.TenantBucketCredentialsRequest;
import it.eng.tools.s3.configuration.S3ClientProvider;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.BucketConnectionVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Slice-level QA test for TB1 (#323): verifies that {@link BucketProvisioningModeResolver}'s
 * classification composes directly with {@link BucketConnectionVerificationService}'s
 * verification, with no adapter/glue code needed, and that no invalid combination ever
 * reaches a verification call.
 */
@ExtendWith(MockitoExtension.class)
class BucketProvisioningCompositionTest {

    private static final String BUCKET_NAME = "my-bucket";
    private static final String ACCESS_KEY = "candidate-access-key";
    private static final String SECRET_KEY = "candidate-secret-key";

    @Mock
    private S3ClientProvider s3ClientProvider;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private S3Client s3Client;

    private final BucketProvisioningModeResolver resolver = new BucketProvisioningModeResolver();
    private BucketConnectionVerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new BucketConnectionVerificationService(s3ClientProvider, s3Properties);
    }

    @Test
    @DisplayName("AUTOMATIC mode resolves without any verification call being attempted")
    void automaticModeRequiresNoVerificationCall() {
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance().build();

        assertEquals(BucketProvisioningMode.AUTOMATIC, resolver.resolve(request));
        verifyNoInteractionsWithS3();
    }

    @Test
    @DisplayName("EXISTING_BUCKET mode resolves without any verification call being attempted")
    void existingBucketModeRequiresNoVerificationCall() {
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName(BUCKET_NAME)
                .build();

        assertEquals(BucketProvisioningMode.EXISTING_BUCKET, resolver.resolve(request));
        verifyNoInteractionsWithS3();
    }

    @Test
    @DisplayName("EXTERNAL_CREDENTIALS mode composes directly into a successful verification call")
    void externalCredentialsModeComposesIntoSuccessfulVerification() {
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3ClientProvider.s3Client(any())).thenReturn(s3Client);

        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName(BUCKET_NAME)
                .accessKey(ACCESS_KEY)
                .secretKey(SECRET_KEY)
                .verifyConnection(true)
                .build();

        BucketProvisioningMode mode = resolver.resolve(request);
        assertEquals(BucketProvisioningMode.EXTERNAL_CREDENTIALS, mode);

        boolean verified = verificationService.verify(
                request.getBucketName(), request.getAccessKey(), request.getSecretKey());

        assertTrue(verified);
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    @DisplayName("EXTERNAL_CREDENTIALS mode composes directly into a rejected verification call")
    void externalCredentialsModeComposesIntoRejectedVerification() {
        when(s3Properties.getRegion()).thenReturn("us-east-1");
        when(s3ClientProvider.s3Client(any())).thenReturn(s3Client);
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder().message("access denied").statusCode(403).build());

        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName(BUCKET_NAME)
                .accessKey(ACCESS_KEY)
                .secretKey(SECRET_KEY)
                .verifyConnection(true)
                .build();

        BucketProvisioningMode mode = resolver.resolve(request);
        assertEquals(BucketProvisioningMode.EXTERNAL_CREDENTIALS, mode);

        boolean verified = verificationService.verify(
                request.getBucketName(), request.getAccessKey(), request.getSecretKey());

        assertFalse(verified);
    }

    @ParameterizedTest
    @MethodSource("invalidCombinations")
    @DisplayName("Invalid combinations are rejected before any verification call could ever occur")
    void invalidCombinationsNeverReachVerification(TenantBucketCredentialsRequest request) {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request));
        verifyNoInteractionsWithS3();
    }

    private void verifyNoInteractionsWithS3() {
        verify(s3ClientProvider, never()).s3Client(any());
        verify(s3Client, never()).headBucket(any(HeadBucketRequest.class));
    }

    private static Stream<TenantBucketCredentialsRequest> invalidCombinations() {
        return Stream.of(
                TenantBucketCredentialsRequest.Builder.newInstance()
                        .accessKey(ACCESS_KEY)
                        .build(),
                TenantBucketCredentialsRequest.Builder.newInstance()
                        .secretKey(SECRET_KEY)
                        .build(),
                TenantBucketCredentialsRequest.Builder.newInstance()
                        .bucketName(BUCKET_NAME)
                        .accessKey(ACCESS_KEY)
                        .build(),
                TenantBucketCredentialsRequest.Builder.newInstance()
                        .bucketName(BUCKET_NAME)
                        .secretKey(SECRET_KEY)
                        .build()
        );
    }
}
