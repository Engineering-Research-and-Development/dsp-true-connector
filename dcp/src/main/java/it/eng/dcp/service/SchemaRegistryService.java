package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * Abstraction for a schema registry (Phase 0 stub).
 */
public interface SchemaRegistryService {

    boolean exists(String schemaId);

    Optional<JsonNode> getSchema(String schemaId);

    void putSchema(String schemaId, JsonNode schema);

    void removeSchema(String schemaId);
}

