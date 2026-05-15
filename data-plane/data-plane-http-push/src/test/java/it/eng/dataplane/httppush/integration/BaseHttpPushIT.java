package it.eng.dataplane.httppush.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
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

import java.util.UUID;

/**
 * Base class for HTTP-PUSH Data Plane integration tests.
 *
 * <p>Starts shared MongoDB and MinIO Testcontainers once per module and exposes
 * MockMvc and WireMock for use by subclass tests.
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
     * Returns a new random URN-style identifier.
     *
     * @return a unique URN string
     */
    protected String newId() {
        return "urn:uuid:" + UUID.randomUUID();
    }
}
