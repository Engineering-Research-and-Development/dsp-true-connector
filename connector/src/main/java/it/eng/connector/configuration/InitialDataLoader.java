package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3ClientService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;

import java.io.File;
import java.io.InputStream;

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
    private final S3Properties s3Properties;
    
    public InitialDataLoader(MongoTemplate mongoTemplate, Environment environment, S3ClientService s3ClientService, S3Properties s3Properties) {
        this.mongoTemplate = mongoTemplate;
        this.environment = environment;
        this.s3ClientService = s3ClientService;
        this.s3Properties = s3Properties;
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
            if(activeProfiles.length == 0) {
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
        };
    }

    /**
     * Loads mock data into S3 when the application is ready.
     * This method is triggered by the ApplicationReadyEvent.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadMockData() {
        log.info("Uploading mock data to S3...");

        try {
            // Create S3 bucket if it doesn't exist
            String bucketName = s3Properties.getBucketName();
            if (!s3ClientService.bucketExists(bucketName)) {
                s3ClientService.createBucket(bucketName);
                log.info("Created S3 bucket: {}", bucketName);
            }

            ClassPathResource file = new ClassPathResource("ENG-employee.json");
            if (file.exists()) {
                // from initial_data.json Artifacts.value which is the same as dataset.id
                String fileKey = "urn:uuid:fdc45798-a222-4955-8baf-ab7fd66ac4d5";
                String contentDisposition = ContentDisposition.attachment()
                        .filename(file.getFile().getName())
                        .build()
                        .toString();

                s3ClientService.uploadFile(FileUtils.openInputStream(file.getFile()), bucketName, fileKey, MediaType.APPLICATION_JSON_VALUE, contentDisposition);
            }
        } catch (Exception e) {
            log.error("Error while loading mock data to S3", e);
        }
    }
}