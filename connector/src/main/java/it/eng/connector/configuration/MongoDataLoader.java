package it.eng.connector.configuration;

import java.io.InputStream;

import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class MongoDataLoader {

    private final MongoTemplate mongoTemplate;

    public MongoDataLoader(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Bean
    CommandLineRunner loadInitialData() {
        return args -> {
            ObjectMapper mapper = new ObjectMapper();
            try (InputStream inputStream = new ClassPathResource("initial_data.json").getInputStream()) {
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