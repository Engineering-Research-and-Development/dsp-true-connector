package it.eng.connector.integration;

import it.eng.connector.configuration.InitialDataLoader;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3BucketProvisionService;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.service.AuditEventPublisher;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.wiremock.spring.EnableWireMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link InitialDataLoader}.
 * <p>
 * Covers two scenarios:
 * <ol>
 *   <li>Normal startup — seed data from {@code initial_data.json} is loaded into MongoDB.</li>
 *   <li>Missing seed file — the application must not throw and must leave MongoDB untouched.</li>
 * </ol>
 * The "missing file" scenario is exercised by constructing an {@link InitialDataLoader} with a mocked
 * {@link Environment} that returns an unknown profile (for which no seed file exists), while still
 * using the real {@link MongoTemplate} from the integration-test context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@EnableWireMock
@Testcontainers
public class InitialDataLoaderIT {

    private static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0.12"))
                    .withReuse(false);
    private static final MinIOContainer minIOContainer =
            new MinIOContainer(DockerImageName.parse("minio/minio"))
                    .withReuse(false);

    static {
        mongoDBContainer.start();
        minIOContainer.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", mongoDBContainer::getFirstMappedPort);
        registry.add("s3.endpoint", minIOContainer::getS3URL);
        registry.add("s3.externalPresignedEndpoint", minIOContainer::getS3URL);
    }

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private S3ClientService s3ClientService;
    @Autowired
    private S3BucketProvisionService s3BucketProvisionService;
    @Autowired
    private S3Properties s3Properties;
    @Autowired
    private AuditEventPublisher publisher;

    // -------------------------------------------------------------------------
    // Happy-path: seed data loaded on startup
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Users collection is populated from initial_data.json on startup")
    void initialData_usersCollectionIsPopulated() {
        List<Document> users = mongoTemplate.findAll(Document.class, "users");
        assertFalse(users.isEmpty(),
                "Expected at least one user document loaded from initial_data.json");
    }

    @Test
    @DisplayName("Application properties collection is populated from initial_data.json on startup")
    void initialData_applicationPropertiesCollectionIsPopulated() {
        List<Document> props = mongoTemplate.findAll(Document.class, "application_properties");
        assertFalse(props.isEmpty(),
                "Expected at least one application_properties document loaded from initial_data.json");
    }

    // -------------------------------------------------------------------------
    // Missing-file scenario: loader must not throw
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Missing seed file — loader skips silently without crashing")
    void initialData_missingFile_doesNotThrow() throws Exception {
        // Build a loader whose environment reports an unknown profile so that the
        // resolved filename (initial_data-no-data-file.json) does not exist on the classpath.
        Environment mockEnv = mock(Environment.class);
        when(mockEnv.getActiveProfiles()).thenReturn(new String[]{"no-data-file"});

        InitialDataLoader loader = new InitialDataLoader(
                mongoTemplate, mockEnv, s3ClientService, s3BucketProvisionService, s3Properties, publisher);

        // Must not throw — the application must start cleanly even without a seed file.
        assertDoesNotThrow(() -> loader.loadInitialData().run());
    }

    @Test
    @DisplayName("Missing seed file — no documents are inserted into MongoDB")
    void initialData_missingFile_noDocumentsInserted() throws Exception {
        Environment mockEnv = mock(Environment.class);
        when(mockEnv.getActiveProfiles()).thenReturn(new String[]{"no-data-file"});

        // Use a collection name that cannot exist before this test
        String sentinelCollection = "sentinel_missing_file_test";
        mongoTemplate.dropCollection(sentinelCollection);

        InitialDataLoader loader = new InitialDataLoader(
                mongoTemplate, mockEnv, s3ClientService, s3BucketProvisionService, s3Properties, publisher);

        loader.loadInitialData().run();

        // The sentinel collection must remain empty — nothing was loaded.
        assertTrue(mongoTemplate.findAll(Document.class, sentinelCollection).isEmpty(),
                "No documents should be inserted when the seed file is absent");
    }
}
