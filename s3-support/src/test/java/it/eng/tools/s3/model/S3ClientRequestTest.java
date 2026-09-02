package it.eng.tools.s3.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link S3ClientRequest} factory methods.
 */
class S3ClientRequestTest {

    @Test
    @DisplayName("from(region, endpoint) creates request with null credentials")
    void from_withoutCredentials_hasNullBucketCredentials() {
        S3ClientRequest req = S3ClientRequest.from("us-east-1", "http://minio:9000");

        assertEquals("us-east-1", req.region());
        assertEquals("http://minio:9000", req.endpointOverride());
        assertNull(req.bucketCredentials());
    }

    @Test
    @DisplayName("from(region, endpoint, credentials) stores all fields")
    void from_withCredentials_storesAllFields() {
        BucketCredentialsEntity creds = BucketCredentialsEntity.Builder.newInstance()
                .bucketName("my-bucket")
                .accessKey("AKID")
                .secretKey("SK")
                .build();

        S3ClientRequest req = S3ClientRequest.from("eu-west-1", "http://minio:9000", creds);

        assertEquals("eu-west-1", req.region());
        assertEquals("http://minio:9000", req.endpointOverride());
        assertSame(creds, req.bucketCredentials());
    }

    @Test
    @DisplayName("from(region, endpoint) with null endpoint override is allowed")
    void from_nullEndpointOverride_isAllowed() {
        S3ClientRequest req = S3ClientRequest.from("us-east-1", null);

        assertEquals("us-east-1", req.region());
        assertNull(req.endpointOverride());
        assertNull(req.bucketCredentials());
    }
}
