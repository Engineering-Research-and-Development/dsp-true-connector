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
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Loads initial MongoDB data and provisions storage for every enabled tenant before startup completes.
 * Optional mock data is uploaded separately when the application is ready.
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
     * Loads initial data into MongoDB and ensures enabled tenants have provisioned storage.
     *
     * @return a CommandLineRunner that loads initial data and provisions tenant storage
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

            ensureTenantBuckets();
        };
    }

    /**
     * Uploads optional mock data to every enabled tenant's provisioned bucket after startup.
     * Mandatory bucket provisioning is completed by {@link #loadInitialData()} before this event.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadMockData() {
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .description("Application started")
                .eventType(AuditEventType.APPLICATION_START)
                .build());
        ClassPathResource file = new ClassPathResource("ENG-employee.json");
        if (!file.exists()) {
            log.debug("Optional mock data file is absent; tenant storage is already provisioned");
            return;
        }
        for (Tenant tenant : tenantRepository.findByEnabled(true)) {
            try {
                uploadMockData(file, tenant.getBucketName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while loading mock data to S3 for tenant '{}'", tenant.getId(), e);
                return;
            } catch (Exception e) {
                log.error("Error while loading mock data to S3 for tenant '{}'", tenant.getId(), e);
            }
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

    private void ensureTenantBuckets() {
        List<Tenant> tenants = tenantRepository.findByEnabled(true);
        if (tenants.isEmpty()) {
            throw new IllegalStateException("No enabled tenants found - connector cannot start");
        }
        for (Tenant tenant : tenants) {
            try {
                String bucketName = tenant.getBucketName();
                boolean needsBucketName = !StringUtils.hasText(bucketName);
                if (needsBucketName) {
                    String bucketPrefix = s3Properties.getBucketName();
                    if (!StringUtils.hasText(bucketPrefix)) {
                        throw new IllegalStateException("No s3.bucketName configured for tenant bucket derivation");
                    }
                    bucketName = bucketPrefix + "-" + tenant.getId();
                }
                s3BucketProvisionService.ensureBucketCredentials(bucketName);
                if (needsBucketName) {
                    // Update only the storage field, preserving tenant metadata and auditing fields.
                    mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(tenant.getId())),
                            new Update().set("bucketName", bucketName), Tenant.class);
                }
                log.info("S3 storage ensured for tenant '{}' in bucket '{}'", tenant.getId(), bucketName);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Failed to provision S3 storage for tenant '" + tenant.getId() + "'", e);
            }
        }
    }

    private void uploadMockData(ClassPathResource file, String bucketName)
            throws IOException, ExecutionException, InterruptedException {
        if (!StringUtils.hasText(bucketName)) {
            throw new IllegalStateException("No provisioned tenant bucket for mock data");
        }
        // This key matches the mock dataset ID in the initial data.
        String fileKey = "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5";
        String contentDisposition = ContentDisposition.attachment()
                .filename(file.getFilename())
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
        log.info("Uploading mock data to S3 bucket '{}'", bucketName);
        try (InputStream inputStream = file.getInputStream()) {
            s3ClientService.uploadFile(inputStream, destinationS3Properties,
                    MediaType.APPLICATION_JSON_VALUE, contentDisposition).get();
        }
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