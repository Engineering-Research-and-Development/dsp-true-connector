package it.eng.dcp.holder.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.Arrays;

@Configuration
@EnableMongoRepositories(basePackages = {
    "it.eng.dcp.holder.repository",
    "it.eng.dcp.common.repository"
})
public class DCPMongoConfig {

    /**
     * Register custom converters for JsonNode to/from MongoDB Document.
     * This resolves Jackson ObjectNode deserialization issues.
     * Only created if no other MongoCustomConversions bean exists.
     * @return MongoCustomConversions with JsonNode converters
     */
    @Bean
    @ConditionalOnMissingBean(MongoCustomConversions.class)
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new JsonNodeToDocumentConverter(),
                new DocumentToJsonNodeConverter()
        ));
    }
}
