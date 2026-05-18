package it.eng.tools.s3.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BucketCredentialsEntity} builder.
 */
class BucketCredentialsEntityTest {

    @Test
    @DisplayName("Builder builds entity with all fields set correctly")
    void builder_setsAllFields() {
        Instant now = Instant.now();

        BucketCredentialsEntity entity = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("my-bucket")
                .accessKey("AKID123")
                .secretKey("secret")
                .issued(now)
                .modified(now)
                .createdBy("admin")
                .lastModifiedBy("admin")
                .version(1L)
                .build();

        assertEquals("my-bucket", entity.getBucketName());
        assertEquals("AKID123", entity.getAccessKey());
        assertEquals("secret", entity.getSecretKey());
        assertEquals(now, entity.getIssued());
        assertEquals(now, entity.getModified());
    }

    @Test
    @DisplayName("Builder builds minimal entity with only required fields")
    void builder_minimalBuild() {
        BucketCredentialsEntity entity = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("minimal-bucket")
                .accessKey("ak")
                .secretKey("sk")
                .build();

        assertEquals("minimal-bucket", entity.getBucketName());
        assertEquals("ak", entity.getAccessKey());
        assertEquals("sk", entity.getSecretKey());
        assertNull(entity.getIssued());
        assertNull(entity.getModified());
    }

    @Test
    @DisplayName("Two Builder.newInstance() calls produce independent instances")
    void builder_newInstance_producesIndependentInstances() {
        BucketCredentialsEntity a = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket-a").accessKey("ak-a").secretKey("sk-a").build();
        BucketCredentialsEntity b = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("bucket-b").accessKey("ak-b").secretKey("sk-b").build();

        assertNotEquals(a.getBucketName(), b.getBucketName());
        assertNotEquals(a.getAccessKey(), b.getAccessKey());
    }
}
