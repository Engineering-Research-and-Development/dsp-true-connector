package it.eng.dcp.holder.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class InMemorySchemaRegistryService implements SchemaRegistryService {

    private final ConcurrentMap<String, JsonNode> store = new ConcurrentHashMap<>();

    @Override
    public boolean exists(String schemaId) {
        return schemaId != null && store.containsKey(schemaId);
    }

    @Override
    public Optional<JsonNode> getSchema(String schemaId) {
        if (schemaId == null) return Optional.empty();
        return Optional.ofNullable(store.get(schemaId));
    }

    @Override
    public void putSchema(String schemaId, JsonNode schema) {
        if (schemaId == null || schemaId.isBlank() || schema == null) {
            throw new IllegalArgumentException("schemaId and schema must be provided");
        }
        store.put(schemaId, schema);
    }

    @Override
    public void removeSchema(String schemaId) {
        if (schemaId == null) return;
        store.remove(schemaId);
    }
}

