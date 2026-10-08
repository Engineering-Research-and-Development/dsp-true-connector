package it.eng.tools.configuration;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import it.eng.tools.model.Tenant;
import lombok.extern.slf4j.Slf4j;

/**
 * Ensures the MongoDB indexes required by the {@link Tenant} collection exist.
 */
@Slf4j
@Component
public class TenantIndexInitializer {

    /** Name of the unique partial index enforcing one tenant per Keycloak realm. */
    public static final String REALM_INDEX_NAME = "tenant_realm_unique";

    private final MongoTemplate mongoTemplate;

    /**
     * Constructs the initializer.
     *
     * @param mongoTemplate the Mongo template used to create indexes
     */
    public TenantIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Creates the unique index on {@code tenants.realm} once the application is ready.
     * The index only covers string values (BSON type 2), so tenants with a missing or explicit
     * null realm never collide; this is the sparse-unique behaviour that tolerates seeded nulls.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        mongoTemplate.indexOps(Tenant.class)
                .createIndex(new Index().on("realm", Sort.Direction.ASC)
                        .named(REALM_INDEX_NAME).unique()
                        .partial(PartialIndexFilter.of(Criteria.where("realm").type(2))));
        log.info("Ensured tenant realm index '{}'", REALM_INDEX_NAME);
    }
}
