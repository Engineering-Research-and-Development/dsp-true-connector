package it.eng.tools.service;

import it.eng.tools.model.BucketProvisioningMode;
import it.eng.tools.model.TenantBucketCredentialsRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BucketProvisioningModeResolverTest {

    private final BucketProvisioningModeResolver resolver = new BucketProvisioningModeResolver();

    @Test
    @DisplayName("Resolves AUTOMATIC when no fields are set")
    void resolvesAutomaticWhenNoFieldsSet() {
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance().build();

        assertEquals(BucketProvisioningMode.AUTOMATIC, resolver.resolve(request));
    }

    @Test
    @DisplayName("Resolves EXISTING_BUCKET when only bucketName is set")
    void resolvesExistingBucketWhenOnlyBucketNameSet() {
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName("my-bucket")
                .build();

        assertEquals(BucketProvisioningMode.EXISTING_BUCKET, resolver.resolve(request));
    }

    @Test
    @DisplayName("Resolves EXTERNAL_CREDENTIALS when bucketName, accessKey, and secretKey are all set")
    void resolvesExternalCredentialsWhenAllFieldsSet() {
        TenantBucketCredentialsRequest request = TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName("my-bucket")
                .accessKey("my-access-key")
                .secretKey("my-secret-key")
                .build();

        assertEquals(BucketProvisioningMode.EXTERNAL_CREDENTIALS, resolver.resolve(request));
    }

    @ParameterizedTest
    @MethodSource("invalidCombinations")
    @DisplayName("Throws IllegalArgumentException for every invalid field combination")
    void throwsForInvalidCombination(TenantBucketCredentialsRequest request) {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request));
    }

    private static Stream<TenantBucketCredentialsRequest> invalidCombinations() {
        return Stream.of(
                // accessKey present without bucketName
                TenantBucketCredentialsRequest.Builder.newInstance()
                        .accessKey("my-access-key")
                        .build(),
                // secretKey present without bucketName
                TenantBucketCredentialsRequest.Builder.newInstance()
                        .secretKey("my-secret-key")
                        .build(),
                // accessKey present without secretKey
                TenantBucketCredentialsRequest.Builder.newInstance()
                        .bucketName("my-bucket")
                        .accessKey("my-access-key")
                        .build(),
                // secretKey present without accessKey
                TenantBucketCredentialsRequest.Builder.newInstance()
                        .bucketName("my-bucket")
                        .secretKey("my-secret-key")
                        .build(),
                // accessKey and secretKey present without bucketName
                TenantBucketCredentialsRequest.Builder.newInstance()
                        .accessKey("my-access-key")
                        .secretKey("my-secret-key")
                        .build()
        );
    }
}
