package it.eng.dataplane.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration for data-plane services.
 *
 * <p>Enables Spring Data MongoDB repositories for data-plane-core and the S3
 * credential repositories from the shared {@code tools} module. The connector
 * module has its own {@code MongoConfig} that covers the full set of packages;
 * this config covers only what data-plane standalone services need.</p>
 *
 * <p>Configures map key dot replacement so that {@code dataAddress} maps whose
 * keys contain dots (e.g. DSP protocol URIs like
 * {@code https://w3id.org/edc/v0.0.1/ns/endpoint}) can be stored in MongoDB,
 * which does not allow dots in document field names.</p>
 */
@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = {
        "it.eng.dataplane.core.repository",
        "it.eng.tools.s3.repository"
})public class DataPlaneMongoConfig {

    /**
     * Configures {@link MappingMongoConverter} with a dot-replacement character
     * so that map keys containing dots (e.g. DSP protocol URIs) are stored safely.
     *
     * @param factory the MongoDB database factory
     * @param context the MongoDB mapping context
     * @return configured converter
     */
    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory factory,
                                                       MongoMappingContext context) {
        DbRefResolver resolver = new DefaultDbRefResolver(factory);
        MappingMongoConverter converter = new MappingMongoConverter(resolver, context);
        converter.setMapKeyDotReplacement("~");
        return converter;
    }
}
