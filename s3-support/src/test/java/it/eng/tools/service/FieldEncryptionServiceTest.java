package it.eng.tools.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FieldEncryptionService}.
 */
class FieldEncryptionServiceTest {

    private FieldEncryptionService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new FieldEncryptionService("test-encryption-key-for-unit-tests");
    }

    @Test
    @DisplayName("encrypt returns a non-blank Base64 string")
    void encrypt_returnsNonBlankBase64() {
        String encrypted = service.encrypt("my-secret");
        assertNotNull(encrypted);
        assertFalse(encrypted.isBlank());
        assertNotEquals("my-secret", encrypted);
    }

    @Test
    @DisplayName("decrypt(encrypt(value)) round-trips to original value")
    void encryptDecrypt_roundTrip() {
        String original = "supersecretpassword";
        String encrypted = service.encrypt(original);
        String decrypted = service.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("Same key produces same ciphertext for same input (deterministic IV)")
    void encrypt_isDeterministic() {
        String first = service.encrypt("value");
        String second = service.encrypt("value");
        assertEquals(first, second);
    }

    @Test
    @DisplayName("Different plaintext values produce different ciphertext")
    void encrypt_differentInputs_differentOutputs() {
        String a = service.encrypt("value-a");
        String b = service.encrypt("value-b");
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("Two services with same key can decrypt each other's ciphertext")
    void crossInstance_sameKey_canDecrypt() throws Exception {
        FieldEncryptionService other = new FieldEncryptionService("test-encryption-key-for-unit-tests");
        String encrypted = service.encrypt("cross-instance-value");
        assertEquals("cross-instance-value", other.decrypt(encrypted));
    }

    @Test
    @DisplayName("decrypt throws RuntimeException for invalid Base64 input")
    void decrypt_invalidInput_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> service.decrypt("not-valid-base64!!!"));
    }
}
