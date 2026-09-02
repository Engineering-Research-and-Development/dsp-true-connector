package it.eng.tools.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyHasherTest {

    private final ApiKeyHasher hasher = new ApiKeyHasher("test-pepper-at-least-32-bytes-long!!");

    @Test
    @DisplayName("hash is deterministic for the same input")
    void hashIsDeterministic() {
        String h1 = hasher.hash("my-api-key");
        String h2 = hasher.hash("my-api-key");
        assertEquals(h1, h2);
    }

    @Test
    @DisplayName("hash differs for different inputs")
    void hashDiffersForDifferentInputs() {
        assertNotEquals(hasher.hash("key-a"), hasher.hash("key-b"));
    }

    @Test
    @DisplayName("hash never contains the raw key as a substring")
    void hashDoesNotLeakRawKey() {
        String raw = "super-secret-dp-key";
        assertFalse(hasher.hash(raw).contains(raw));
    }

    @Test
    @DisplayName("matches returns true for the correct raw key")
    void matchesReturnsTrueForCorrectKey() {
        String hash = hasher.hash("my-api-key");
        assertTrue(hasher.matches("my-api-key", hash));
    }

    @Test
    @DisplayName("matches returns false for an incorrect raw key")
    void matchesReturnsFalseForIncorrectKey() {
        String hash = hasher.hash("my-api-key");
        assertFalse(hasher.matches("wrong-key", hash));
    }

    @Test
    @DisplayName("matches returns false for null raw key")
    void matchesReturnsFalseForNullRawKey() {
        String hash = hasher.hash("my-api-key");
        assertFalse(hasher.matches(null, hash));
    }

    @Test
    @DisplayName("constructor rejects a blank pepper")
    void constructorRejectsBlankPepper() {
        assertThrows(IllegalStateException.class, () -> new ApiKeyHasher(""));
    }

    @Test
    @DisplayName("constructor rejects a pepper shorter than 32 bytes")
    void constructorRejectsShortPepper() {
        assertThrows(IllegalStateException.class, () -> new ApiKeyHasher("too-short"));
    }
}
