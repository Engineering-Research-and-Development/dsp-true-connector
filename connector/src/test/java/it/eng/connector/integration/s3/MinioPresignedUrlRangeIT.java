package it.eng.connector.integration.s3;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Standalone integration test that verifies whether MinIO honours an HTTP {@code Range} header
 * added to an already-signed presigned GET URL.
 *
 * <p>This test does NOT use Testcontainers or Spring Boot. It connects directly to the local
 * MinIO instance whose credentials are taken from {@code application-consumer.properties}:
 * <ul>
 *   <li>endpoint: http://localhost:9000</li>
 *   <li>accessKey/secretKey: minioadmin / minioadmin</li>
 *   <li>bucket: dsp-true-connector-consumer</li>
 * </ul>
 *
 * <p><b>Prerequisite:</b> MinIO must be running locally before executing this test
 * (e.g. via {@code docker compose -f ci/docker/docker-compose.yml up -d}).
 */
@Slf4j
@Disabled("Requires a local MinIO instance on localhost:9000 — run manually with docker compose up")
public class MinioPresignedUrlRangeIT {

    // Credentials from application-consumer.properties
    private static final String ENDPOINT     = "http://localhost:9000";
    private static final String ACCESS_KEY   = "minioadmin";
    private static final String SECRET_KEY   = "minioadmin";
    private static final String REGION       = "us-east-1";
    private static final String BUCKET_NAME  = "dsp-true-connector-consumer";
    private static final String OBJECT_KEY   = "range-header-test-30mb";

    private static final int FILE_SIZE_BYTES = 30 * 1024 * 1024; // 30 MB

    private S3Client s3Client;
    private S3Presigner presigner;

    @BeforeEach
    void setUp() {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY));

        s3Client = S3Client.builder()
                .credentialsProvider(credentials)
                .region(Region.of(REGION))
                .endpointOverride(URI.create(ENDPOINT))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();

        presigner = S3Presigner.builder()
                .credentialsProvider(credentials)
                .region(Region.of(REGION))
                .endpointOverride(URI.create(ENDPOINT))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();

        // Ensure the bucket exists
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(BUCKET_NAME).build());
            log.info("Bucket {} already exists", BUCKET_NAME);
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
            log.info("Created bucket {}", BUCKET_NAME);
        }
    }

    @AfterEach
    void tearDown() {
        // Best-effort cleanup of the test object
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(OBJECT_KEY)
                    .build());
            log.info("Cleaned up test object {}/{}", BUCKET_NAME, OBJECT_KEY);
        } catch (Exception e) {
            log.warn("Could not delete test object during cleanup: {}", e.getMessage());
        }
        if (presigner != null) {
            presigner.close();
        }
        if (s3Client != null) {
            s3Client.close();
        }
    }

    @Test
    @DisplayName("Range header on presigned GET URL returns 206 — not a signature mismatch error")
    void rangeHeaderOnPresignedUrlShouldReturn206() throws Exception {
        // --- Step 1: Upload a 30 MB file with random content ---
        byte[] data = new byte[FILE_SIZE_BYTES];
        new Random().nextBytes(data);

        log.info("Uploading 30 MB test object to MinIO ({}/{})", BUCKET_NAME, OBJECT_KEY);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(OBJECT_KEY)
                        .contentType("application/octet-stream")
                        .build(),
                RequestBody.fromBytes(data));
        log.info("Upload complete");

        // --- Step 2: Generate a presigned GET URL (7-day expiry, no Range in signature) ---
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(OBJECT_KEY)
                .build();

        PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofDays(7))
                        .getObjectRequest(getObjectRequest)
                        .build());

        String presignedUrl = presignedRequest.url().toExternalForm();
        log.info("Presigned URL generated (Range header NOT included in signature): {}", presignedUrl);

        // Confirm: the presigned URL's X-Amz-SignedHeaders must not include "range"
        assertNotNull(presignedUrl);
        String lowerUrl = presignedUrl.toLowerCase();
        log.info("X-Amz-SignedHeaders value in URL: {}",
                lowerUrl.contains("x-amz-signedheaders=")
                        ? presignedUrl.substring(lowerUrl.indexOf("x-amz-signedheaders="))
                                .split("&")[0]
                        : "not found");

        // --- Step 3: Add Range header — start from the middle of the file (byte 15 MB) ---
        long rangeStart = FILE_SIZE_BYTES / 2L; // 15 728 640
        URL url = new URL(presignedUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("Range", "bytes=" + rangeStart + "-");

        log.info("Sending GET request with Range: bytes={}-", rangeStart);
        int responseCode = connection.getResponseCode();
        log.info("MinIO response code: {}", responseCode);
        log.info("Content-Range header: {}", connection.getHeaderField("Content-Range"));
        log.info("Content-Length header: {}", connection.getHeaderField("Content-Length"));

        // --- Step 4: Assert response code before reading body ---
        // 206 Partial Content = MinIO honoured the Range header on a presigned URL (design is valid)
        // 403 Forbidden       = MinIO treated the Range header as URL tampering (design is invalid)
        // 416 Range Not Satisfiable = Range was parsed but out of bounds (unexpected for mid-file offset)
        assertEquals(206, responseCode,
                "Expected HTTP 206 Partial Content — MinIO should honour a Range header " +
                "that was NOT included in the presigned URL signature. " +
                "If 403, the Range header invalidates the signature and the resume design must be revisited.");

        // --- Step 5: Read and count the downloaded bytes ---
        long downloadedBytes;
        try (InputStream responseBody = connection.getInputStream();
             java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
            responseBody.transferTo(buffer);
            downloadedBytes = buffer.size();
        }
        connection.disconnect();

        long expectedBytes = FILE_SIZE_BYTES - rangeStart;
        log.info("Downloaded {} bytes (expected ~{} bytes = second half of 30 MB file)",
                downloadedBytes, expectedBytes);
        log.info("Download size matches expected remainder: {}", downloadedBytes == expectedBytes);

        assertEquals(expectedBytes, downloadedBytes,
                "Downloaded byte count should equal the second half of the file (" +
                expectedBytes + " bytes), confirming the Range header was applied correctly.");
    }
}
