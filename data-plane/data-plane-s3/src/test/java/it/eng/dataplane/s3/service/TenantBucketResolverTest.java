package it.eng.dataplane.s3.service;

import it.eng.tools.s3.properties.S3Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TenantBucketResolver}.
 */
@ExtendWith(MockitoExtension.class)
class TenantBucketResolverTest {

    @Mock
    private S3Properties s3Properties;

    @InjectMocks
    private TenantBucketResolver resolver;

    @Test
    @DisplayName("resolveBucketName() returns configured fallback bucket name")
    void resolveBucketName_returnsConfiguredBucket() {
        when(s3Properties.getBucketName()).thenReturn("my-bucket");

        assertEquals("my-bucket", resolver.resolveBucketName());
    }

    @Test
    @DisplayName("resolveBucketName(tenantId) ignores tenantId and returns configured fallback bucket name")
    void resolveBucketName_withTenantId_ignoresTenantAndReturnsBucket() {
        when(s3Properties.getBucketName()).thenReturn("my-bucket");

        assertEquals("my-bucket", resolver.resolveBucketName("some-tenant"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("resolveBucketName() throws IllegalStateException when fallback bucket not configured")
    void resolveBucketName_throwsWhenBucketBlank(String bucketName) {
        when(s3Properties.getBucketName()).thenReturn(bucketName);

        IllegalStateException exception = assertThrows(IllegalStateException.class, resolver::resolveBucketName);

        assertTrue(exception.getMessage().contains("fallback"));
        assertTrue(exception.getMessage().contains("s3.bucketName"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("resolveBucketName(tenantId) throws IllegalStateException when fallback bucket not configured")
    void resolveBucketName_withTenantId_throwsWhenBucketBlank(String bucketName) {
        when(s3Properties.getBucketName()).thenReturn(bucketName);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> resolver.resolveBucketName("tenant-1"));

        assertTrue(exception.getMessage().contains("fallback"));
        assertTrue(exception.getMessage().contains("s3.bucketName"));
    }

    @Test
    @DisplayName("resolveBucketName() and resolveBucketName(tenantId) return the same fallback value")
    void bothOverloads_returnSameBucketName() {
        when(s3Properties.getBucketName()).thenReturn("shared-bucket");

        assertEquals(resolver.resolveBucketName(), resolver.resolveBucketName("ignored-tenant"));
    }
}
