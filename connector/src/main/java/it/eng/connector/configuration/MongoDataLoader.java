package it.eng.connector.configuration;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.InputStream;
import java.util.Map;

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
            InputStream inputStream = new ClassPathResource("initial_data.json").getInputStream();
            JsonNode rootNode = mapper.readTree(inputStream);

            rootNode.fields().forEachRemaining(entry -> {
                String collectionName = entry.getKey();
                JsonNode documents = entry.getValue();

                documents.forEach(document -> {
                    Map<String, Object> documentMap = mapper.convertValue(document, new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
                    mongoTemplate.save(documentMap, collectionName);
                });
                System.out.println("Loaded " + documents.size() + " documents into the '" + collectionName + "' collection.");
            });
        };
    }
}




