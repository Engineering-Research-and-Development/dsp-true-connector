package it.eng.dcp.common.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.util.SelfSignedCertGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.UUID.randomUUID;

/**
 * Service for managing cryptographic keys used in DCP operations.
 * Handles key loading, rotation, and JWK conversion.
 */
@Service
public class KeyService {
    private static final Logger log = LoggerFactory.getLogger(KeyService.class);

    private final KeyMetadataService keyMetadataService;

    @Autowired
    public KeyService(KeyMetadataService keyMetadataService) {
        this.keyMetadataService = keyMetadataService;
    }

    private KeyPair keyPair;

    // TTL for key cache in seconds (default: 1 day)
    private long keyCacheTtlSeconds = 86400;

    // CachedKey structure for caching with expiry - keyed by (keystorePath + alias)
    private static class CachedKey {
        final KeyPair keyPair;
        final Instant expiresAt;
        CachedKey(KeyPair keyPair, Instant expiresAt) {
            this.keyPair = keyPair;
            this.expiresAt = expiresAt;
        }
    }
    // Cache key format: "keystorePath::alias"
    private final Map<String, CachedKey> keyPairCache = new ConcurrentHashMap<>();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Loads a key pair with caching and active alias resolution from metadata.
     * This is the primary method for loading key pairs that should be used by other methods.
     *
     * The method:
     * 1. Resolves the active alias from keyMetadataService (falls back to config alias)
     * 2. Checks cache for existing valid key pair
     * 3. Loads from keystore if not cached or expired
     * 4. Caches the loaded key pair with TTL
     *
     * @param config DidDocumentConfig containing keystore details
     * @return The loaded KeyPair for the active alias
     */
    public KeyPair loadKeyPairWithActiveAlias(DidDocumentConfig config) {
        // Get active key alias from metadata
        String activeAlias = keyMetadataService.getActiveKeyMetadata()
                .map(it.eng.dcp.common.model.KeyMetadata::getAlias)
                .orElse(config.getKeystoreAlias());

        String cacheKey = config.getKeystorePath() + "::" + activeAlias;
        Instant now = Instant.now();

        // Check cache first
        CachedKey cached = keyPairCache.get(cacheKey);
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return cached.keyPair;
        }

        // Load from keystore
        KeyPair loaded = loadKeyPairFromP12(config.getKeystorePath(), config.getKeystorePassword(), activeAlias);

        // Cache with TTL
        keyPairCache.put(cacheKey, new CachedKey(loaded, now.plusSeconds(keyCacheTtlSeconds)));

        return loaded;
    }

    private KeyPair loadKeyPairFromP12(String resourcePath, String password, String alias) {
        // Normalize resource path: strip 'classpath:' and leading '/'
        String normalizedPath = resourcePath;
        if (normalizedPath.startsWith("classpath:")) {
            normalizedPath = normalizedPath.substring("classpath:".length());
        }
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        InputStream is = null;
        try {
            // First, try loading from filesystem
            File file = new File(normalizedPath);
            if (file.exists()) {
                is = new FileInputStream(file);
            } else {
                // Fallback to classpath
                is = getClassLoader().getResourceAsStream(normalizedPath);
            }
            if (is == null) {
                throw new RuntimeException("Resource not found in filesystem or classpath: " + normalizedPath);
            }
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(is, password.toCharArray());
            PrivateKey privateKey = (PrivateKey) keystore.getKey(alias, password.toCharArray());
            Certificate cert = keystore.getCertificate(alias);
            keyPair = new KeyPair(cert.getPublicKey(), privateKey);
            return keyPair;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load KeyPair from PKCS12", e);
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignore) {}
            }
        }
    }

    /**
     * Invalidates all cached keys.
     * Useful for clearing cache after key rotation or configuration changes.
     */
    public void invalidateAllCache() {
        keyPairCache.clear();
    }

    /**
     * Gets the current key pair for the given config, loading it from default location if not already loaded.
     *
     * @deprecated Use {@link #loadKeyPairWithActiveAlias(DidDocumentConfig)} instead for proper caching and alias resolution
     * @param config DidDocumentConfig containing keystore details
     * @return The current KeyPair
     */
    @Deprecated
    public KeyPair getKeyPair(DidDocumentConfig config) {
        return loadKeyPairWithActiveAlias(config);
    }

    /**
     * Generates a key ID (kid) from the public key using SHA-256 hash.
     * Loads the key pair if not provided.
     *
     * @param config DidDocumentConfig containing keystore details
     * @return Base64-URL encoded key ID
     */
    public String getKidFromPublicKey(DidDocumentConfig config) {
        KeyPair keyPair = loadKeyPairWithActiveAlias(config);
        return getKidFromPublicKey(config, keyPair);
    }

    /**
     * Generates a key ID (kid) from the provided public key using SHA-256 hash.
     *
     * @param config DidDocumentConfig containing keystore details (for context)
     * @param keyPair The key pair to use
     * @return Base64-URL encoded key ID
     */
    public String getKidFromPublicKey(DidDocumentConfig config, KeyPair keyPair) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(keyPair.getPublic().getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate kid", e);
        }
    }

    /**
     * Returns a Nimbus ECKey containing public and private parts suitable for signing.
     * Loads the key pair if not provided.
     *
     * @param config DidDocumentConfig containing keystore details
     * @return ECKey for signing
     */
    public ECKey getSigningJwk(DidDocumentConfig config) {
        KeyPair kp = loadKeyPairWithActiveAlias(config);
        return getSigningJwk(config, kp);
    }

    /**
     * Returns a Nimbus ECKey containing public and private parts suitable for signing.
     * Uses the provided key pair and does not perform any loading.
     *
     * @param config DidDocumentConfig containing keystore details (for context)
     * @param keyPair The key pair to use
     * @return ECKey for signing
     */
    public ECKey getSigningJwk(DidDocumentConfig config, KeyPair keyPair) {
        try {
            if (keyPair == null) throw new IllegalStateException("No KeyPair available");
            ECPublicKey pub = (ECPublicKey) keyPair.getPublic();
            ECPrivateKey priv = (ECPrivateKey) keyPair.getPrivate();
            return new ECKey.Builder(Curve.forECParameterSpec(pub.getParams()), pub)
                    .privateKey(priv)
                    .keyID(getKidFromPublicKey(config, keyPair))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build signing JWK", e);
        }
    }

    /**
     * Converts a BigInteger to Base64-URL encoded string, removing leading zeros.
     *
     * @param value The BigInteger value
     * @return Base64-URL encoded string
     */
    private String toBase64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // Remove leading zero byte if present
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] tmp = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, tmp, 0, tmp.length);
            bytes = tmp;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Converts the current public key to JWK (JSON Web Key) format.
     * Loads the key pair if not provided.
     *
     * @param config DidDocumentConfig containing keystore details
     * @return Map containing JWK parameters
     */
    public Map<String, Object> convertPublicKeyToJWK(DidDocumentConfig config) {
        KeyPair kp = loadKeyPairWithActiveAlias(config);
        return convertPublicKeyToJWK(config, kp);
    }

    /**
     * Converts the provided public key to JWK (JSON Web Key) format.
     *
     * @param config DidDocumentConfig containing keystore details (for context)
     * @param keyPair The key pair to use
     * @return Map containing JWK parameters
     */
    public Map<String, Object> convertPublicKeyToJWK(DidDocumentConfig config, KeyPair keyPair) {
        String x = toBase64Url(((ECPublicKey) keyPair.getPublic()).getW().getAffineX());
        String y = toBase64Url(((ECPublicKey) keyPair.getPublic()).getW().getAffineY());
        String d = toBase64Url(((ECPrivateKey) keyPair.getPrivate()).getS());
        String kid = config.getDid() + "#" + getKidFromPublicKey(config, keyPair);
        return Map.of(
                "kty", "EC",
                "d", d,
                "use", "sig",
                "crv", "P-256",
                "kid", kid,
                "x", x,
                "y", y);
    }

    /**
     * Rotates the key pair and persists it to the keystore.
     *
     * @param keystorePath Path to the keystore file
     * @param password     Keystore password
     * @param aliasPrefix  New key alias
     */
    public void rotateAndPersistKeyPair(String keystorePath, String password, String aliasPrefix) {
        log.debug("Starting key rotation: keystorePath={}, aliasPrefix={}", keystorePath, aliasPrefix);
        try {
            // Generate new EC key pair
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair newKeyPair = kpg.generateKeyPair();
            log.debug("Generated new EC key pair");

            // Load existing keystore (keep KeyStore instance outside try block)
            KeyStore ks = KeyStore.getInstance("PKCS12");
            String subjectDn = "CN=DidKey"; // fallback subject
            try (FileInputStream fis = new FileInputStream(keystorePath)) {
                ks.load(fis, password.toCharArray());
                log.debug("Loaded keystore from {}", keystorePath);
                // Try to get the active alias from metadata or fallback to first alias
                String activeAlias = null;
                java.util.Enumeration<String> aliases = ks.aliases();
                if (aliases.hasMoreElements()) {
                    activeAlias = aliases.nextElement();
                }
                if (activeAlias != null) {
                    Certificate existingCert = ks.getCertificate(activeAlias);
                    if (existingCert instanceof java.security.cert.X509Certificate) {
                        subjectDn = ((java.security.cert.X509Certificate) existingCert).getSubjectX500Principal().getName();
                        log.debug("Preserved subject DN from existing certificate: {}", subjectDn);
                    }
                }
            }
            // Generate self-signed certificate with preserved subject
            Certificate cert = SelfSignedCertGenerator.generate(subjectDn, newKeyPair);
            log.debug("Generated self-signed certificate for new key");
            // Generate new alias
            log.debug("Using new alias: {}", aliasPrefix);
            // Add new key/cert to keystore
            ks.setKeyEntry(aliasPrefix, newKeyPair.getPrivate(), password.toCharArray(), new Certificate[]{cert});
            log.debug("Added new key entry to keystore under alias: {}", aliasPrefix);
            // Write updated keystore back to file
            try (FileOutputStream fos = new FileOutputStream(keystorePath)) {
                ks.store(fos, password.toCharArray());
                log.debug("Stored updated keystore to {}", keystorePath);
            }
        } catch (Exception e) {
            log.error("Failed to rotate and persist KeyPair: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to rotate and persist KeyPair", e);
        }
    }

    /**
     * Generates a new EC key pair using the secp256r1 curve.
     *
     * @return The generated KeyPair
     * @throws NoSuchAlgorithmException     if EC algorithm is not available
     * @throws InvalidAlgorithmParameterException if the curve parameters are invalid
     * @throws NoSuchProviderException      if BouncyCastle provider is not available
     */
    private KeyPair generateEcKeyPair() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, NoSuchProviderException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", "BC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
        keyGen.initialize(ecSpec, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    /**
     * Generates a new EC key using Nimbus library.
     *
     * @return The generated ECKey
     */
    public ECKey generateEcKey() {
        try {
            return new ECKeyGenerator(Curve.P_256)
                    .keyID(randomUUID().toString())
                    .keyUse(KeyUse.SIGNATURE)
                    .generate();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Rotates the EC key, persists it in the keystore, and updates key metadata in MongoDB.
     *
     * @param keystorePath Path to the PKCS12 keystore file
     * @param password     Keystore password
     * @param exitingAlias The existing alias to base the new alias on
     * @return The new key alias
     */
    @Transactional
    public String rotateKeyAndUpdateMetadata(String keystorePath, String password, String exitingAlias) {
        // Generate a unique alias for the new key
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String alias = exitingAlias + "-" + timestamp + "-" + randomUUID().toString().substring(0, 8);

        // Rotate and persist the new key
        rotateAndPersistKeyPair(keystorePath, password, alias);

        // Update key metadata in MongoDB
        keyMetadataService.saveNewKeyMetadata(alias);

        // Invalidate cache to ensure fresh keys are loaded
        invalidateAllCache();

        return alias;
    }

    // For testability: allow mocking the classloader
    protected ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }
}
