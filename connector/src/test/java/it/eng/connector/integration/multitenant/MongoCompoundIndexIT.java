package it.eng.connector.integration.multitenant;

import it.eng.connector.integration.BaseIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the compound MongoDB indexes required for tenant-scoped queries are
 * created at application startup by {@code InitialDataLoader.createCompoundIndexes()}.
 */
public class MongoCompoundIndexIT extends BaseIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final Map<String, List<String>> EXPECTED_INDEXES = Map.of(
            "catalogs",               List.of("tenantId", "_id"),
            "datasets",               List.of("tenantId", "_id"),
            "contract_negotiations",  List.of("tenantId", "state", "role"),
            "transfer_process",       List.of("tenantId", "state", "role"),
            "agreements",             List.of("tenantId", "id"),
            "audit_events",           List.of("tenantId", "timestamp"),
            "application_properties", List.of("tenantId", "_id")
    );

    @Test
    @DisplayName("Compound tenantId indexes are created on all primary multi-tenant collections at startup")
    void compoundIndexes_existOnAllTargetCollections() {
        EXPECTED_INDEXES.forEach((collectionName, expectedFields) -> {
            List<Document> indexes = StreamSupport.stream(
                    mongoTemplate.getCollection(collectionName).listIndexes().spliterator(), false)
                    .collect(Collectors.toList());

            boolean found = indexes.stream().anyMatch(idx -> {
                Document key = idx.get("key", Document.class);
                if (key == null) {
                    return false;
                }
                return expectedFields.stream().allMatch(field -> key.containsKey(field));
            });

            assertTrue(found,
                    "Expected compound index with fields " + expectedFields
                            + " not found on collection '" + collectionName + "'");
        });
    }
}
