package it.eng.dcp.holder.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

/**
 * Converts JsonNode to MongoDB Document for storage.
 */
@WritingConverter
public class JsonNodeToDocumentConverter implements Converter<JsonNode, Document> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Document convert(JsonNode source) {
        if (source == null) {
            return null;
        }
        try {
            // Convert JsonNode to a Map that MongoDB can store
            return Document.parse(MAPPER.writeValueAsString(source));
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JsonNode to Document", e);
        }
    }
}

