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

import static org.junit.jupiter.api.Assertions.*;
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
    @DisplayName("resolveBucketName() returns configured bucket name")
    void resolveBucketName_returnsConfiguredBucket() {
        when(s3Properties.getBucketName()).thenReturn("my-bucket");

        assertEquals("my-bucket", resolver.resolveBucketName());
    }

    @Test
    @DisplayName("resolveBucketName(tenantId) ignores tenantId and returns configured bucket name")
    void resolveBucketName_withTenantId_ignoresTenantAndReturnsBucket() {
        when(s3Properties.getBucketName()).thenReturn("my-bucket");

        assertEquals("my-bucket", resolver.resolveBucketName("some-tenant"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("resolveBucketName() throws IllegalStateException when bucket not configured")
    void resolveBucketName_throwsWhenBucketBlank(String bucketName) {
        when(s3Properties.getBucketName()).thenReturn(bucketName);

        assertThrows(IllegalStateException.class, resolver::resolveBucketName);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("resolveBucketName(tenantId) throws IllegalStateException when bucket not configured")
    void resolveBucketName_withTenantId_throwsWhenBucketBlank(String bucketName) {
        when(s3Properties.getBucketName()).thenReturn(bucketName);

        assertThrows(IllegalStateException.class, () -> resolver.resolveBucketName("tenant-1"));
    }

    @Test
    @DisplayName("resolveBucketName() and resolveBucketName(tenantId) return same value")
    void bothOverloads_returnSameBucketName() {
        when(s3Properties.getBucketName()).thenReturn("shared-bucket");

        assertEquals(resolver.resolveBucketName(), resolver.resolveBucketName("ignored-tenant"));
    }
}
