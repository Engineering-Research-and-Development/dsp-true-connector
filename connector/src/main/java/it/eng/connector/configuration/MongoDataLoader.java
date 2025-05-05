package it.eng.connector.configuration;

import java.io.InputStream;

import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class MongoDataLoader {

    private final MongoTemplate mongoTemplate;
    private final Environment environment;
    
    public MongoDataLoader(MongoTemplate mongoTemplate, Environment environment) {
        this.mongoTemplate = mongoTemplate;
        this.environment = environment;
    }

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

                        documents.forEach(document -> {
                            Document mongoDocument = Document.parse(document.toString());
                            mongoTemplate.save(mongoDocument, collectionName);                        });
                        log.info("Loaded " + documents.size() + " documents into the '" + collectionName + "' collection.");
                });
            } catch (Exception e) {
                log.error("Error loading initial data: " + e.getMessage());
                throw new RuntimeException("Failed to load initial data", e);
            }
        };
    }
}