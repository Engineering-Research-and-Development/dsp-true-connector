package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
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
import org.apache.commons.io.FileUtils;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;

import java.io.InputStream;
import java.util.Map;

/**
 * InitialDataLoader is responsible for loading initial data into MongoDB and uploading mock data to S3.
 * It uses CommandLineRunner to load data when the application starts and ApplicationReadyEvent to upload data to S3.
 */
@Slf4j
@Configuration
public class InitialDataLoader {

    private final MongoTemplate mongoTemplate;
    private final Environment environment;
    private final S3ClientService s3ClientService;
    private final S3BucketProvisionService s3BucketProvisionService;
    private final S3Properties s3Properties;
    private final AuditEventPublisher publisher;
    private final TenantRepository tenantRepository;

    public InitialDataLoader(MongoTemplate mongoTemplate, Environment environment, S3ClientService s3ClientService,
                             S3BucketProvisionService s3BucketProvisionService, S3Properties s3Properties,
                             AuditEventPublisher publisher, TenantRepository tenantRepository) {
        this.mongoTemplate = mongoTemplate;
        this.environment = environment;
        this.s3ClientService = s3ClientService;
        this.s3BucketProvisionService = s3BucketProvisionService;
        this.s3Properties = s3Properties;
        this.publisher = publisher;
        this.tenantRepository = tenantRepository;
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
            try (InputStream inputStream = new ClassPathResource(filename).getInputStream()) {
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
                            // Use native driver replaceOne (upsert) to bypass Spring Data entity mapping.
                            // mongoTemplate.save() triggers MappingMongoConverter which strips fields
                            // annotated with @JsonIgnore (e.g. tenantId, bucketName) because Spring Data
                            // detects the _class discriminator and applies entity-aware write processing.
                            // The native replaceOne preserves the raw BSON document as-is.
                            Document existingDocument = mongoTemplate.findById(documentId, Document.class, collectionName);
                            mongoTemplate.getCollection(collectionName).replaceOne(
                                    Filters.eq("_id", documentId),
                                    mongoDocument,
                                    new ReplaceOptions().upsert(true));
                            if (existingDocument == null) {
                                newDocuments++;
                            } else {
                                log.debug("Document with ID {} already exists in collection '{}', replacing...",
                                        documentId, collectionName);
                                skippedDocuments++;
                            }
                        } else {
                            // If document has no ID, insert as new document (no upsert needed).
                            mongoTemplate.getCollection(collectionName).insertOne(mongoDocument);
                            newDocuments++;
                        }
                    }

                    log.info("Collection '{}': {} new documents loaded, {} documents replaced (already existed).",
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

            ClassPathResource file = new ClassPathResource("ENG-employee.json");
            if (file.exists()) {
                // from initial_data.json Artifacts.value which is the same as dataset.id
                String fileKey = "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5";
                String contentDisposition = ContentDisposition.attachment()
                        .filename(file.getFile().getName())
                        .build()
                        .toString();

                Map<String, String> destinationS3Properties = Map.of(
                        S3Utils.OBJECT_KEY, fileKey,
                        S3Utils.BUCKET_NAME, bucketName,
                        S3Utils.ENDPOINT_OVERRIDE, s3Properties.getEndpoint(),
                        S3Utils.REGION, s3Properties.getRegion(),
                        S3Utils.ACCESS_KEY, s3Properties.getAccessKey(),
                        S3Utils.SECRET_KEY, s3Properties.getSecretKey()
                );

                s3ClientService.uploadFile(
                                FileUtils.openInputStream(file.getFile()),
                                destinationS3Properties,
                                MediaType.APPLICATION_JSON_VALUE,
                                contentDisposition)
                        .get();
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

    /**
     * Creates compound MongoDB indexes for all primary multi-tenant collections at startup.
     * Index creation via {@link com.mongodb.client.MongoCollection#createIndex} is idempotent —
     * safe to call on every application start without side effects.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createCompoundIndexes() {
        log.info("Creating compound MongoDB indexes for multi-tenant collections...");
        createIndex("catalogs",               new Document("tenantId", 1).append("_id", 1));
        createIndex("datasets",               new Document("tenantId", 1).append("_id", 1));
        createIndex("contract_negotiations",  new Document("tenantId", 1).append("state", 1).append("role", 1));
        createIndex("transfer_process",       new Document("tenantId", 1).append("state", 1).append("role", 1));
        // "agreements" is keyed by a tenant-independent technical _id (see Agreement.technicalId),
        // because the DSP protocol "id" is legitimately shared between a provider's and a consumer's
        // local copies of the same agreement. Index and enforce uniqueness on (tenantId, id) instead.
        createIndex("agreements",             new Document("tenantId", 1).append("id", 1), true);
        createIndex("audit_events",           new Document("tenantId", 1).append("timestamp", 1));
        createIndex("application_properties", new Document("tenantId", 1).append("_id", 1));
        log.info("Compound MongoDB indexes created (or already exist).");
    }

    /**
     * Creates a non-unique MongoDB index on the given collection with the given key document.
     *
     * @param collectionName the name of the MongoDB collection
     * @param keys           the index key document
     */
    private void createIndex(String collectionName, Document keys) {
        createIndex(collectionName, keys, false);
    }

    /**
     * Creates a MongoDB index on the given collection with the given key document.
     * Errors are logged as warnings rather than propagated, so a failed index creation
     * does not prevent the application from starting.
     *
     * @param collectionName the name of the MongoDB collection
     * @param keys           the index key document
     * @param unique         whether the index should enforce uniqueness on its key combination
     */
    private void createIndex(String collectionName, Document keys, boolean unique) {
        try {
            mongoTemplate.getCollection(collectionName)
                    .createIndex(keys, new IndexOptions().unique(unique));
            log.debug("Index ensured on collection '{}'", collectionName);
        } catch (Exception e) {
            log.warn("Could not create index on collection '{}': {}", collectionName, e.getMessage());
        }
    }
}