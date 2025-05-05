package it.eng.tools.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * A custom serializer for Java's {@link Instant} class to format it as an ISO-8601 zoned date-time string.
 * This serializer converts an {@link Instant} to a string representation using the system's default time zone.
 */
public class InstantSerializer extends JsonSerializer<Instant> {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Serializes an {@link Instant} object to JSON as a string in ISO-8601 zoned date-time format.
     *
     * @param value       the {@link Instant} value to be serialized
     * @param gen         the {@link JsonGenerator} used to write JSON content
     * @param serializers the {@link SerializerProvider} that can be used to get serializers for serializing objects
     * @throws IOException if an I/O error occurs during serialization
     */
    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(formatter.format(value.atZone(java.time.ZoneId.systemDefault())));
    }
}
