package it.eng.dcp.issuer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for CredentialObjectIds.
 */
class CredentialObjectIdsTest {

    @Test
    @DisplayName("newId returns a urn:uuid URN wrapping a valid UUID")
    void newId_isUrnUuid() {
        String id = CredentialObjectIds.newId();

        assertTrue(id.startsWith("urn:uuid:"));
        String uuid = id.substring("urn:uuid:".length());
        assertDoesNotThrow(() -> UUID.fromString(uuid));
        assertEquals(uuid, UUID.fromString(uuid).toString());
    }

    @Test
    @DisplayName("newId returns a different identifier on each call")
    void newId_isUnique() {
        assertNotEquals(CredentialObjectIds.newId(), CredentialObjectIds.newId());
    }
}
