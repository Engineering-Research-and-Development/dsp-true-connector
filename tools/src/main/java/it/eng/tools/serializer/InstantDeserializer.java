package it.eng.tools.serializer;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A custom deserializer for Java's {@link Instant} class to parse it from an ISO-8601 zoned date-time string.
 * This deserializer converts a string representation of a zoned date-time to an {@link Instant}.
 */
public class InstantDeserializer extends StdDeserializer<Instant> {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public InstantDeserializer() {
        super(Instant.class);
    }

    /**
     * Deserializes a JSON string to an {@link Instant} object.
     *
     * @param p    the {@link com.fasterxml.jackson.core.JsonParser} used to parse JSON content
     * @param ctxt the {@link DeserializationContext} that can be used to access contextual information
     * @return the deserialized {@link Instant} object
     * @throws IOException if an I/O error occurs during deserialization
     */
    @Override
    public Instant deserialize(com.fasterxml.jackson.core.JsonParser p, DeserializationContext ctxt) throws IOException {
        return ZonedDateTime.parse(p.getText(), formatter).toInstant();
    }
}