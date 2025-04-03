package it.eng.datatransfer.migration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

/**
 * Configuration for MongoDB GridFS.
 */
@Configuration
public class MongoGridFsConfiguration {

    /**
     * Creates a GridFsTemplate bean for interacting with MongoDB GridFS.
     * 
     * @param mongoTemplate the MongoDB template
     * @return a GridFsTemplate instance
     */
    @Bean
    public GridFsTemplate gridFsTemplate(MongoTemplate mongoTemplate) {
        return new GridFsTemplate(mongoTemplate.getMongoDatabaseFactory(), mongoTemplate.getConverter());
    }
}
