package it.eng.dataplane.httppush.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.minio.admin.MinioAdminClient;
import io.minio.admin.UserInfo;
import it.eng.dataplane.httppush.TestDataPlaneHttpPushApplication;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base class for HTTP-PUSH Data Plane integration tests.
 *
 * <p>Starts shared MongoDB and MinIO Testcontainers once per module and exposes
 * MockMvc, WireMock, and S3/IAM helper utilities for use by subclass tests.
 * Authentication uses the {@code X-Api-Key} header matching {@link #API_KEY}.
 */
@SpringBootTest(
    classes = TestDataPlaneHttpPushApplication.class,
    webEnvironment = WebEnvironment.DEFINED_PORT,
    properties = {
        "server.port=0",
        "dataplane.control-plane-admin-endpoint=",
        "dataplane.api-key=" + BaseHttpPushIT.API_KEY,
        "application.encryption.key=5m7mlhmu65zsp6x"
    }
)
@AutoConfigureMockMvc
@EnableWireMock
public class BaseHttpPushIT {

    /** API key used for {@code X-Api-Key} authentication in all HTTP-PUSH integration tests. */
    public static final String API_KEY = "test-dp-api-key";

    /**
     * Bucket name matching {@code s3.bucketName} in {@code application.properties}.
     * The bucket is provisioned by {@code DataPlaneS3StartupBean} on {@code ApplicationReadyEvent}.
     */
    public static final String TEST_BUCKET_NAME = "dsp-true-connector-provider";
    public static final String TEST_BUCKET_NAME_DESTINATION = "dsp-true-connector-consumer";

    /** Shared MongoDB container — started once per module via static init. */
    protected static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0.12"))
                    .withReuse(false);

    /** Shared MinIO container — started once per module via static init. */
    protected static final MinIOContainer minIOContainer =
            new MinIOContainer(DockerImageName.parse("minio/minio"))
                    .withReuse(false);

    static {
        mongoDBContainer.start();
        minIOContainer.start();
    }

    /** WireMock server for stubbing external HTTP calls. */
    @InjectWireMock
    protected WireMockServer wireMock;

    /** MockMvc for dispatching requests through the DispatcherServlet. */
    @Autowired
    protected MockMvc mockMvc;

    /** Jackson ObjectMapper for building and parsing JSON bodies. */
    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Registers dynamic Spring properties from the running Testcontainers instances.
     *
     * @param registry the Spring property registry
     */
    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () ->
                "mongodb://" + mongoDBContainer.getHost() + ":" + mongoDBContainer.getFirstMappedPort() + "/data-plane-push");
        registry.add("s3.endpoint", minIOContainer::getS3URL);
        registry.add("s3.externalPresignedEndpoint", minIOContainer::getS3URL);
        registry.add("s3.accessKey", minIOContainer::getUserName);
        registry.add("s3.secretKey", minIOContainer::getPassword);
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    /**
     * Adds the {@code X-Api-Key} header to the given request builder.
     *
     * @param builder the request builder
     * @return the builder with the API-key header added
     */
    protected MockHttpServletRequestBuilder withApiKey(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Api-Key", API_KEY);
    }

    /**
     * Uploads UTF-8 text content to the test MinIO bucket using admin credentials.
     *
     * <p>Use from subclass {@code @BeforeAll} methods to pre-populate objects that the
     * application code (e.g. presigned URL generation in {@code HttpPushTransferProtocol})
     * will reference during the test. The bucket is created idempotently.</p>
     *
     * @param key     S3 object key
     * @param content UTF-8 text to store
     */
    protected static void uploadToTestMinIO(String key, String content) {
        try (S3Client s3 = buildAdminS3Client()) {
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET_NAME).build());
            } catch (Exception ignored) {
                // Bucket already exists — expected after DataPlaneS3StartupBean ran
            }
            s3.putObject(
                    PutObjectRequest.builder().bucket(TEST_BUCKET_NAME).key(key).build(),
                    RequestBody.fromString(content));
        }
    }

    /**
     * Checks whether an S3 object with the given key exists in the test MinIO bucket.
     *
     * @param key the S3 object key to check
     * @return {@code true} if the object exists, {@code false} otherwise
     */
    protected static boolean objectExistsInMinIO(String key, String bucketName) {
        try (S3Client s3 = buildAdminS3Client()) {
            s3.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Downloads and returns the UTF-8 content of an S3 object from the test MinIO bucket.
     *
     * @param key the S3 object key to download
     * @return the object content as a UTF-8 string
     * @throws IOException if the object content cannot be read
     */
    protected static String downloadContentFromMinIO(String key, String bucketName) throws IOException {
        try (S3Client s3 = buildAdminS3Client()) {
            var response = s3.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build());
            return new String(response.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Polls MinIO until the specified object key appears in the test bucket or the timeout elapses.
     *
     * @param key            the S3 object key to wait for
     * @param timeoutSeconds maximum seconds to wait before failing the test
     * @throws InterruptedException if the polling thread is interrupted
     */
    protected static void awaitObjectExists(String key, int timeoutSeconds, String bucketName) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (objectExistsInMinIO(key, bucketName)) {
                return;
            }
            Thread.sleep(300);
        }
        fail("Object '" + key + "' did not appear in bucket '" + bucketName + "' within " + timeoutSeconds + "s");
    }

    /**
     * Creates a MinIO IAM temporary user and attaches a PutObject-only policy scoped to
     * the exact {@code bucketName/objectKey} resource — mirroring the logic in
     * {@code TemporaryBucketUserService} used by the consumer Control Plane.
     *
     * <p>Use this in e2e tests to provision consumer-side credentials that the HTTP-PUSH
     * provider Data Plane will use to push an artifact directly into the consumer bucket.</p>
     *
     * @param transferProcessId the transfer process ID — used as part of the policy name
     * @param bucketName        the target bucket
     * @param objectKey         the exact object key the temp user is allowed to write
     * @return a map with keys {@code bucketName}, {@code objectKey}, {@code accessKey},
     *         {@code secretKey}, {@code region}, {@code endpointOverride}
     */
    protected static Map<String, String> createTempUserAndPolicy(String transferProcessId,
                                                                  String bucketName,
                                                                  String objectKey) {
        String accessKey = "TempUser-" + UUID.randomUUID().toString().substring(0, 8);
        String secretKey = UUID.randomUUID().toString();
        String policyName = "temp-tp-policy-" + transferProcessId;
        String policyJson = buildTempUserPolicyJson(bucketName, objectKey);

        try {
            MinioAdminClient adminClient = buildMinioAdminClient();
            adminClient.addUser(accessKey, UserInfo.Status.ENABLED, secretKey, null, null);
            adminClient.addCannedPolicy(policyName, policyJson);
            adminClient.setPolicy(accessKey, false, policyName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temp MinIO user/policy for " + transferProcessId, e);
        }

        Map<String, String> credentials = new HashMap<>();
        credentials.put("bucketName", bucketName);
        credentials.put("objectKey", objectKey);
        credentials.put("accessKey", accessKey);
        credentials.put("secretKey", secretKey);
        credentials.put("region", "us-east-1");
        credentials.put("endpointOverride", minIOContainer.getS3URL());
        return credentials;
    }

    /**
     * Builds an S3Client configured for the test MinIO container using admin credentials.
     * Callers must close the returned client (use try-with-resources).
     *
     * @return a configured {@link S3Client}
     */
    protected static S3Client buildAdminS3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(minIOContainer.getS3URL()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minIOContainer.getUserName(), minIOContainer.getPassword())))
                .region(Region.of("us-east-1"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /**
     * Builds a {@link MinioAdminClient} connected to the test MinIO container using admin credentials.
     *
     * @return a configured {@link MinioAdminClient}
     */
    protected static MinioAdminClient buildMinioAdminClient() {
        return MinioAdminClient.builder()
                .endpoint(minIOContainer.getS3URL())
                .credentials(minIOContainer.getUserName(), minIOContainer.getPassword())
                .build();
    }

    /**
     * Returns a new random URN-style identifier.
     *
     * @return a unique URN string
     */
    protected String newId() {
        return "urn:uuid:" + UUID.randomUUID();
    }

    private static String buildTempUserPolicyJson(String bucketName, String objectKey) {
        return String.format("""
                {
                    "Version": "2012-10-17",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Action": ["s3:PutObject"],
                            "Resource": ["arn:aws:s3:::%s/%s"]
                        }
                    ]
                }
                """, bucketName, objectKey);
    }
}
