package it.eng.tools.s3.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TemporaryBucketUser} builder.
 */
class TemporaryBucketUserTest {

    @Test
    @DisplayName("Builder builds entity with all fields set correctly")
    void builder_setsAllFields() {
        Instant now = Instant.now();

        TemporaryBucketUser user = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId("tp-123")
                .accessKey("AKID123")
                .secretKey("secret")
                .bucketName("push-bucket")
                .objectKey("data/file.bin")
                .issued(now)
                .modified(now)
                .createdBy("admin")
                .lastModifiedBy("admin")
                .version(1L)
                .build();

        assertEquals("tp-123", user.getTransferProcessId());
        assertEquals("AKID123", user.getAccessKey());
        assertEquals("secret", user.getSecretKey());
        assertEquals("push-bucket", user.getBucketName());
        assertEquals("data/file.bin", user.getObjectKey());
        assertEquals(now, user.getIssued());
        assertEquals(now, user.getModified());
    }

    @Test
    @DisplayName("Builder builds minimal entity with only required fields")
    void builder_minimalBuild() {
        TemporaryBucketUser user = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId("tp-minimal")
                .accessKey("ak")
                .secretKey("sk")
                .bucketName("b")
                .objectKey("k")
                .build();

        assertEquals("tp-minimal", user.getTransferProcessId());
        assertNull(user.getIssued());
        assertNull(user.getModified());
    }

    @Test
    @DisplayName("Two Builder.newInstance() calls produce independent instances")
    void builder_newInstance_producesIndependentInstances() {
        TemporaryBucketUser a = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId("tp-a").accessKey("ak-a").secretKey("sk-a")
                .bucketName("b").objectKey("k").build();
        TemporaryBucketUser b = TemporaryBucketUser.Builder.newInstance()
                .transferProcessId("tp-b").accessKey("ak-b").secretKey("sk-b")
                .bucketName("b").objectKey("k").build();

        assertNotEquals(a.getTransferProcessId(), b.getTransferProcessId());
    }
}
