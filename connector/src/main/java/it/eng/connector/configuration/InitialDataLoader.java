package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.Tenant;
import it.eng.tools.repository.TenantRepository;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3BucketProvisionService;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

/**
 * InitialDataLoader is responsible for loading initial data into MongoDB and uploading mock data to S3.
 * It uses CommandLineRunner to load data when the application starts and ApplicationReadyEvent to upload data to S3.
 */
@Slf4j
@Configuration
public class InitialDataLoader {

    private static final String DEFAULT_MOCK_DATA_RESOURCE = "classpath:ENG-employee.json";
    private static final String MOCK_DATA_RESOURCE_PROPERTY = "application.mock.data.resource";
    private static final String DEFAULT_MOCK_DATA_FILENAME = "transfer-data.bin";

    private final MongoTemplate mongoTemplate;
    private final Environment environment;
    private final S3ClientService s3ClientService;
    private final S3BucketProvisionService s3BucketProvisionService;
    private final S3Properties s3Properties;
    private final AuditEventPublisher publisher;
    private final TenantRepository tenantRepository;
    private final ResourceLoader resourceLoader;

    public InitialDataLoader(MongoTemplate mongoTemplate, Environment environment, S3ClientService s3ClientService,
                             S3BucketProvisionService s3BucketProvisionService, S3Properties s3Properties,
                             AuditEventPublisher publisher, TenantRepository tenantRepository,
                             ResourceLoader resourceLoader) {
        this.mongoTemplate = mongoTemplate;
        this.environment = environment;
        this.s3ClientService = s3ClientService;
        this.s3BucketProvisionService = s3BucketProvisionService;
        this.s3Properties = s3Properties;
        this.publisher = publisher;
        this.tenantRepository = tenantRepository;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Loads initial data into MongoDB when the application starts.
     * This method is triggered by the CommandLineRunner.
     *
     * @return a CommandLineRunner that loads initial data
     */
    @Bean
    CommandLineRunner loadInitialData() {
        return args -> {
            ObjectMapper mapper = new ObjectMapper();
            String filename = null;
            String[] activeProfiles = environment.getActiveProfiles();
            if (activeProfiles.length == 0) {
                log.debug("No active profiles set, using initial_data.json for populating Mongo");
                filename = "initial_data.json";
            } else {
                String activeProfile = activeProfiles[0];
                filename = "initial_data-" + activeProfile + ".json";
                log.debug("Active profile set {}, using {} for populating Mongo", activeProfile, filename);
            }
            try (InputStream inputStream = resourceLoader.getResource("classpath:" + filename).getInputStream()) {
                JsonNode rootNode = mapper.readTree(inputStream);

                rootNode.fields().forEachRemaining(entry -> {
                    String collectionName = entry.getKey();
                    JsonNode documents = entry.getValue();
                    int newDocuments = 0;
                    int skippedDocuments = 0;

                    for (JsonNode document : documents) {
                        Document mongoDocument = Document.parse(document.toString());
                        Object documentId = mongoDocument.get("_id");

                        if (documentId != null) {
                            // Check if document already exists
                            Document existingDocument = mongoTemplate.findById(documentId, Document.class, collectionName);
                            if (existingDocument == null) {
                                mongoTemplate.save(mongoDocument, collectionName);
                                newDocuments++;
                            } else {
                                log.debug("Document with ID {} already exists in collection '{}', skipping...",
                                        documentId, collectionName);
                                skippedDocuments++;
                            }
                        } else {
                            // If document has no ID, treat as new document
                            mongoTemplate.save(mongoDocument, collectionName);
                            newDocuments++;
                        }
                    }

                    log.info("Collection '{}': {} new documents loaded, {} documents skipped (already exist).",
                            collectionName, newDocuments, skippedDocuments);
                });

            } catch (Exception e) {
                log.error("Error loading initial data: {}", e.getMessage());
                throw new RuntimeException("Failed to load initial data", e);
            }

            if (tenantRepository.findByEnabled(true).isEmpty()) {
                throw new IllegalStateException("No enabled tenants found — connector cannot start");
            }
        };
    }

    /**
     * Loads mock data into S3 when the application is ready.
     * This method is triggered by the ApplicationReadyEvent.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadMockData() {
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .description("Application started")
                .eventType(AuditEventType.APPLICATION_START)
                .build());
        log.info("Uploading mock data to S3...");

        try {
            // Resolve bucket name: use the first enabled tenant's bucket, or fall back to global S3 config
            String bucketName = tenantRepository.findAll().stream()
                    .filter(t -> t.isEnabled() && t.getBucketName() != null && !t.getBucketName().isBlank())
                    .findFirst()
                    .map(Tenant::getBucketName)
                    .orElse(s3Properties.getBucketName());
            // Provision the bucket and per-tenant IAM user/credentials, but use admin credentials
            // to upload the mock file — the per-bucket user's secret is stored encrypted and is
            // only needed for presigned-URL generation at request time.
            s3BucketProvisionService.ensureBucketCredentials(bucketName);

            String mockDataResourceLocation = environment.getProperty(MOCK_DATA_RESOURCE_PROPERTY,
                    DEFAULT_MOCK_DATA_RESOURCE);
            Resource mockDataResource = resourceLoader.getResource(mockDataResourceLocation);
            if (mockDataResource.exists()) {
                // from initial_data.json Artifacts.value which is the same as dataset.id
                String fileKey = "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5";
                String filename = Objects.requireNonNullElse(mockDataResource.getFilename(), DEFAULT_MOCK_DATA_FILENAME);
                String contentDisposition = ContentDisposition.attachment()
                        .filename(filename)
                        .build()
                        .toString();
                String contentType = MediaTypeFactory.getMediaType(filename)
                        .map(MediaType::toString)
                        .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);

                Map<String, String> destinationS3Properties = Map.of(
                        S3Utils.OBJECT_KEY, fileKey,
                        S3Utils.BUCKET_NAME, bucketName,
                        S3Utils.ENDPOINT_OVERRIDE, s3Properties.getEndpoint(),
                        S3Utils.REGION, s3Properties.getRegion(),
                        S3Utils.ACCESS_KEY, s3Properties.getAccessKey(),
                        S3Utils.SECRET_KEY, s3Properties.getSecretKey()
                );

                try (InputStream inputStream = mockDataResource.getInputStream()) {
                    s3ClientService.uploadFile(
                                    inputStream,
                                    destinationS3Properties,
                                    contentType,
                                    contentDisposition)
                            .get();
                }
            } else {
                log.warn("Mock data resource {} does not exist, skipping S3 mock upload", mockDataResourceLocation);
            }
        } catch (Exception e) {
            log.error("Error while loading mock data to S3", e);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void onApplicationShutdown(ContextClosedEvent event) {
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .description("Application stopped")
                .eventType(AuditEventType.APPLICATION_STOP)
                .build());
    }
}