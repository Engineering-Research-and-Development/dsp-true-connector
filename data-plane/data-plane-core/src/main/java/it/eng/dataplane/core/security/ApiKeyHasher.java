package it.eng.dataplane.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes keyed HMAC-SHA256 hashes of this Data Plane's own {@code dataplane.api-key}, so it can
 * be compared against the hashed value the Control Plane sends back in the {@code X-Api-Key}
 * header on CP→DP calls.
 *
 * <p>The Control Plane never learns or forwards the raw API key: it hashes the raw key presented
 * at registration time ({@code it.eng.tools.security.ApiKeyHasher} on the Control Plane side) and
 * persists/sends only that hash. This class is a deliberately standalone duplicate of that same
 * HMAC-SHA256 construction — {@code data-plane-core} must not depend on the {@code tools} module,
 * so the algorithm, pepper property name ({@code dataplane.registration.key-pepper}), and hex
 * encoding are kept in lockstep by hand rather than shared via a common dependency.
 */
@Component
public class ApiKeyHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_PEPPER_BYTES = 32;

    private final SecretKeySpec pepperKey;

    /**
     * Constructs the hasher with the configured pepper.
     *
     * @param pepper the shared secret mixed into every hash, read from
     *               {@code dataplane.registration.key-pepper}; must match the pepper configured
     *               on the Control Plane and be at least 32 bytes of raw UTF-8 data
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
     * @return the hex-encoded hash
     */
    public String hash(String rawApiKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(pepperKey);
            byte[] digest = mac.doFinal(rawApiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute API key hash", e);
        }
    }
}
