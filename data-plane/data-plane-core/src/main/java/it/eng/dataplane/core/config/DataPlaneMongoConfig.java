package it.eng.dataplane.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * MongoDB configuration for data-plane services.
 *
 * <p>Enables Spring Data MongoDB repositories for data-plane-core and the S3
 * credential repositories from the shared {@code tools} module. The connector
 * module has its own {@code MongoConfig} that covers the full set of packages;
 * this config covers only what data-plane standalone services need.</p>
 */
@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = {
        "it.eng.dataplane.core.repository",
        "it.eng.dataplane.s3.repository"
})
public class DataPlaneMongoConfig {
}
