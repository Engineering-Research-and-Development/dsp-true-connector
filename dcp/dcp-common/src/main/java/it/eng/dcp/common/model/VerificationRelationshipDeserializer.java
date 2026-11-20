package it.eng.dcp.common.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deserializes a DID document verification relationship (for example {@code capabilityInvocation}).
 *
 * <p>Per DID Core, each entry is either a reference to a verification method (an absolute DID URL or
 * a relative {@code #fragment}) or an embedded verification method object. Both forms are mapped to the
 * verification method ID; for an embedded object that is its {@code id} property. Entries without a
 * usable ID are skipped.
 */
public class VerificationRelationshipDeserializer extends JsonDeserializer<List<String>> {

    /** DID Core {@code id} property of an embedded verification method (not the JSON-LD {@code @id}). */
    private static final String ID_PROPERTY = "id";

    /**
     * Reads a relationship array into the list of referenced verification method IDs.
     *
     * @param parser the JSON parser positioned at the relationship value
     * @param context the deserialization context
     * @return the referenced verification method IDs, never null
     * @throws IOException if the JSON cannot be read
     */
    @Override
    public List<String> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.readValueAsTree();
        List<String> refs = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return refs;
        }
        for (JsonNode entry : node) {
            if (entry.isTextual() && !entry.asText().isBlank()) {
                refs.add(entry.asText());
            } else if (entry.isObject() && entry.hasNonNull(ID_PROPERTY)) {
                refs.add(entry.get(ID_PROPERTY).asText());
            }
        }
        return refs;
    }
}
