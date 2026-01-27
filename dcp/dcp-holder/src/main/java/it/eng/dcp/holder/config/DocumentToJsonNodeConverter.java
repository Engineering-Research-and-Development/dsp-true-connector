package it.eng.dcp.holder.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * Converts MongoDB Document to JsonNode for reading.
 */
@ReadingConverter
public class DocumentToJsonNodeConverter implements Converter<Document, JsonNode> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public JsonNode convert(Document source) {
        if (source == null) {
            return null;
        }
        try {
            // Convert Document to JsonNode
            String json = source.toJson();
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert Document to JsonNode", e);
        }
    }
}

