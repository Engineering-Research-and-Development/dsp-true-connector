package it.eng.dataplane.httppull.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import it.eng.dataplane.httppull.TestDataPlaneHttpPullApplication;
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.UUID;

/**
 * Base class for HTTP-PULL Data Plane integration tests.
 *
 * <p>Starts shared MongoDB and MinIO Testcontainers once per module and exposes
 * MockMvc and WireMock for use by subclass tests.
 * Authentication uses the {@code X-Api-Key} header matching {@link #API_KEY}.
 */
@SpringBootTest(
    classes = TestDataPlaneHttpPullApplication.class,
    webEnvironment = WebEnvironment.DEFINED_PORT,
    properties = {
        "server.port=0",
        "dataplane.control-plane-admin-endpoint=",
        "dataplane.api-key=" + BaseHttpPullIT.API_KEY,
        "application.encryption.key=5m7mlhmu65zsp6x"
    }
)
@AutoConfigureMockMvc
@EnableWireMock
public class BaseHttpPullIT {

    /** API key used for {@code X-Api-Key} authentication in all HTTP-PULL integration tests. */
    public static final String API_KEY = "test-dp-api-key";

    /**
     * Bucket name matching {@code s3.bucketName} in {@code application.properties}.
     * The bucket is provisioned by {@code DataPlaneS3StartupBean} on {@code ApplicationReadyEvent}.
     */
    public static final String TEST_BUCKET_NAME = "dsp-true-connector-consumer";

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
                "mongodb://" + mongoDBContainer.getHost() + ":" + mongoDBContainer.getFirstMappedPort() + "/data-plane-pull");
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
     * <p>Use this from subclass {@code @BeforeAll} methods to pre-populate objects that the
     * application code will reference during the test (e.g. for presigned URL generation).
     * The bucket is created by {@code DataPlaneS3StartupBean} on startup, so it is guaranteed
     * to exist by the time {@code @BeforeAll} runs.</p>
     *
     * @param key     S3 object key
     * @param content UTF-8 text to store
     */
    protected static void uploadToTestMinIO(String key, String content) {
        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(minIOContainer.getS3URL()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minIOContainer.getUserName(), minIOContainer.getPassword())))
                .region(Region.of("us-east-1"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            // Ensure bucket exists (idempotent) before putting the object
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
     * Returns a new random URN-style identifier.
     *
     * @return a unique URN string
     */
    protected String newId() {
        return "urn:uuid:" + UUID.randomUUID();
    }
}
