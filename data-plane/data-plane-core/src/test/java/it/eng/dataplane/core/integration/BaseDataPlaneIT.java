package it.eng.dataplane.core.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.wiremock.spring.InjectWireMock;
import com.github.tomakehurst.wiremock.WireMockServer;

import java.util.UUID;

/**
 * Base class for Data Plane integration tests.
 *
 * <p>Manages shared MongoDB and MinIO Testcontainers (started once per module via static init)
 * and exposes a WireMock server for stubbing outbound HTTP calls.
 * Authentication is performed via the {@code X-Api-Key} header matching {@link #API_KEY}.
 *
 * <p>Subclasses must declare all Spring test annotations directly
 * ({@code @SpringBootTest}, {@code @AutoConfigureMockMvc}, {@code @EnableWireMock}) because
 * Spring Boot Test annotation processing requires them on the concrete test class.
 * Container property registration via {@link #containerProperties} and
 * the {@link #resetWireMock()} setup method are inherited by JUnit 5 regardless.
 */
public abstract class BaseDataPlaneIT {

    /** API key used for {@code X-Api-Key} authentication in all Data Plane integration tests. */
    public static final String API_KEY = "test-dp-api-key";

    /** Shared MongoDB container — started once and reused across all tests in a module. */
    protected static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0.12"))
                    .withReuse(false);

    /** Shared MinIO container — started once and reused across all tests in a module. */
    protected static final MinIOContainer minIOContainer =
            new MinIOContainer(DockerImageName.parse("minio/minio"))
                    .withReuse(false);

    static {
        mongoDBContainer.start();
        minIOContainer.start();
    }

    /** WireMock server for stubbing external HTTP calls (e.g. Control Plane callbacks). */
    @InjectWireMock
    protected WireMockServer wireMock;

    /** MockMvc for dispatching requests directly through the DispatcherServlet. */
    @Autowired
    protected MockMvc mockMvc;

    /** Jackson ObjectMapper for building and parsing JSON request/response bodies. */
    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Registers dynamic Spring properties from the running Testcontainers instances.
     * Inherited and picked up by Spring Test's {@code DynamicPropertyRegistry} in subclasses.
     *
     * @param registry the Spring property registry
     */
    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", mongoDBContainer::getFirstMappedPort);
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
     * Adds the {@code X-Api-Key} authentication header to a {@link MockHttpServletRequestBuilder}.
     *
     * @param builder the request builder to authenticate
     * @return the same builder with the API-key header set
     */
    protected MockHttpServletRequestBuilder withApiKey(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Api-Key", API_KEY);
    }

    /**
     * Creates a new random URN-style ID suitable for use as a process or dataset identifier.
     *
     * @return a unique URN string
     */
    protected String newId() {
        return "urn:uuid:" + UUID.randomUUID();
    }
}
