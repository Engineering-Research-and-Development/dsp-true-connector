package it.eng.dcp.model;

import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class KeyMetadataTest {

    @Test
    void build_generatesIdAndCreatedAt_whenMissing() {
        KeyMetadata meta = KeyMetadata.Builder.newInstance()
                .alias("alias1")
                .build();

        assertNotNull(meta.getId());
        assertTrue(meta.getId().startsWith("urn:uuid:"));
        assertNotNull(meta.getCreatedAt());
        assertFalse(meta.isActive());
        assertFalse(meta.isArchived());
    }

    @Test
    void build_throwsValidationException_whenAliasMissing() {
        assertThrows(ValidationException.class, () -> KeyMetadata.Builder.newInstance().build());
    }

    @Test
    void build_setsArchivedAt_whenArchivedTrue_withoutArchivedAt() {
        KeyMetadata meta = KeyMetadata.Builder.newInstance()
                .alias("a")
                .archived(true)
                .build();

        assertTrue(meta.isArchived());
        assertNotNull(meta.getArchivedAt());
    }

    @Test
    void build_preservesProvidedIdAndCreatedAt() {
        String id = "urn:uuid:1234";
        Instant now = Instant.parse("2020-01-01T00:00:00Z");

        KeyMetadata meta = KeyMetadata.Builder.newInstance()
                .id(id)
                .alias("a")
                .createdAt(now)
                .build();

        assertEquals(id, meta.getId());
        assertEquals(now, meta.getCreatedAt());
    }
}

