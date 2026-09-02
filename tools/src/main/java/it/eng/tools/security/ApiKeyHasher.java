package it.eng.tools.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes and verifies keyed HMAC-SHA256 hashes of high-entropy API keys (Data Plane
 * registration/callback credentials), so the Control Plane can persist a deterministic,
 * indexable, non-reversible digest instead of the raw secret.
 *
 * <p>Deliberately not BCrypt: BCrypt's per-call random salt makes an indexed exact-match lookup
 * (as used by {@code DataPlaneRegistrationRepository#findByApiKey}) impossible, and its
 * built-in slowness defends against brute-forcing low-entropy human passwords — a concern that
 * does not apply to machine-generated, high-entropy API keys.
 */
@Slf4j
@Component
public class ApiKeyHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_PEPPER_BYTES = 32;

    private final SecretKeySpec pepperKey;

    /**
     * Constructs the hasher with the configured pepper.
     *
     * @param pepper the shared secret mixed into every hash, read from
     *               {@code dataplane.registration.key-pepper}; must be at least 32 bytes of raw
     *               UTF-8 data
     * @throws IllegalStateException if the pepper is blank or shorter than 32 bytes
     */
    public ApiKeyHasher(@Value("${dataplane.registration.key-pepper:}") String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException(
                    "Property 'dataplane.registration.key-pepper' must be configured with a non-blank value.");
        }
        byte[] pepperBytes = pepper.getBytes(StandardCharsets.UTF_8);
        if (pepperBytes.length < MIN_PEPPER_BYTES) {
            throw new IllegalStateException(
                    "Property 'dataplane.registration.key-pepper' must be at least " + MIN_PEPPER_BYTES
                            + " bytes (256 bits) of raw UTF-8 data; found " + pepperBytes.length + " byte(s).");
        }
        this.pepperKey = new SecretKeySpec(pepperBytes, HMAC_ALGORITHM);
    }

    /**
     * Computes the hex-encoded HMAC-SHA256 hash of the given raw API key.
     *
     * @param rawApiKey the raw, plaintext API key
     * @return the hex-encoded hash, safe to persist
     */
    public String hash(String rawApiKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(pepperKey);
            byte[] digest = mac.doFinal(rawApiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute API key hash", e);
        }
    }

    /**
     * Verifies that {@code candidateRawApiKey} hashes to {@code storedHash}, using a
     * constant-time comparison to avoid leaking timing information about the stored hash.
     *
     * @param candidateRawApiKey the raw key presented by the caller; may be {@code null}
     * @param storedHash         the previously-persisted hash to compare against
     * @return {@code true} if the candidate key hashes to the stored value
     */
    public boolean matches(String candidateRawApiKey, String storedHash) {
        if (candidateRawApiKey == null || candidateRawApiKey.isBlank() || storedHash == null) {
            return false;
        }
        String candidateHash = hash(candidateRawApiKey);
        return MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
