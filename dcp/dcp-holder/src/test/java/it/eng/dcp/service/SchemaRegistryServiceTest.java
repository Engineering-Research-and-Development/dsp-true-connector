package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.eng.dcp.holder.service.InMemorySchemaRegistryService;
import it.eng.dcp.holder.service.SchemaRegistryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SchemaRegistryServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("put/get/remove schema lifecycle")
    void putGetRemoveSchemaLifecycle() {
        SchemaRegistryService svc = new InMemorySchemaRegistryService();

        String id = "schema:1";
        assertFalse(svc.exists(id));
        assertEquals(Optional.empty(), svc.getSchema(id));

        ObjectNode node = mapper.createObjectNode();
        node.put("name", "TestSchema");

        svc.putSchema(id, node);
        assertTrue(svc.exists(id));

        Optional<JsonNode> loaded = svc.getSchema(id);
        assertTrue(loaded.isPresent());
        assertEquals("TestSchema", loaded.get().get("name").asText());

        svc.removeSchema(id);
        assertFalse(svc.exists(id));
        assertEquals(Optional.empty(), svc.getSchema(id));
    }

    @Test
    @DisplayName("putSchema rejects null or blank id or null schema")
    void putSchemaRejectsInvalidInputs() {
        SchemaRegistryService svc = new InMemorySchemaRegistryService();
        ObjectNode node = mapper.createObjectNode();
        node.put("k", "v");

        assertThrows(IllegalArgumentException.class, () -> svc.putSchema(null, node));
        assertThrows(IllegalArgumentException.class, () -> svc.putSchema("", node));
        assertThrows(IllegalArgumentException.class, () -> svc.putSchema("  ", node));
        assertThrows(IllegalArgumentException.class, () -> svc.putSchema("id", null));
    }
}

