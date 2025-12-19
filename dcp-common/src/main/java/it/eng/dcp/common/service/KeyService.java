package it.eng.dcp.common.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import it.eng.dcp.common.util.SelfSignedCertGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

import static java.util.UUID.randomUUID;

/**
 * Service for managing cryptographic keys used in DCP operations.
 * Handles key loading, rotation, and JWK conversion.
 */
@Service
public class KeyService {

    private final KeyMetadataService keyMetadataService;

    @Autowired
    public KeyService(KeyMetadataService keyMetadataService) {
        this.keyMetadataService = keyMetadataService;
    }

    private KeyPair keyPair;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Loads a key pair from a PKCS12 keystore.
     *
     * @param resourcePath Path to the keystore resource
     * @param password     Keystore password
     * @param alias        Key alias in the keystore
     * @return The loaded KeyPair
     */
    public KeyPair loadKeyPairFromP12(String resourcePath, String password, String alias) {
        // Normalize resource path: strip 'classpath:' and leading '/'
        String normalizedPath = resourcePath;
        if (normalizedPath.startsWith("classpath:")) {
            normalizedPath = normalizedPath.substring("classpath:".length());
        }
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(normalizedPath)) {
            if (is == null) {
                throw new RuntimeException("Resource not found in classpath: " + normalizedPath);
            }
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(is, password.toCharArray());
            PrivateKey privateKey = (PrivateKey) keystore.getKey(alias, password.toCharArray());
            Certificate cert = keystore.getCertificate(alias);
            keyPair = new KeyPair(cert.getPublicKey(), privateKey);
            return keyPair;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load KeyPair from PKCS12", e);
        }
    }

    /**
     * Gets the current key pair, loading it from default location if not already loaded.
     *
     * @return The current KeyPair
     */
    public KeyPair getKeyPair() {
        if (keyPair == null) {
            keyPair = loadKeyPairFromP12("eckey.p12", "password", "dsptrueconnector");
        }
        return keyPair;
    }

    /**
     * Generates a key ID (kid) from the public key using SHA-256 hash.
     *
     * @return Base64-URL encoded key ID
     */
    public String getKidFromPublicKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(getKeyPair().getPublic().getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate kid", e);
        }
    }

    /**
     * Returns a Nimbus ECKey containing public and private parts suitable for signing.
     * This centralizes building the EC JWK from the loaded KeyPair.
     *
     * @return ECKey for signing
     */
    public ECKey getSigningJwk() {
        try {
            KeyPair kp = getKeyPair();
            if (kp == null) throw new IllegalStateException("No KeyPair available");
            ECPublicKey pub = (ECPublicKey) kp.getPublic();
            ECPrivateKey priv = (ECPrivateKey) kp.getPrivate();
            return new ECKey.Builder(Curve.forECParameterSpec(pub.getParams()), pub)
                    .privateKey(priv)
                    .keyID(getKidFromPublicKey())
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
     *
     * @return Map containing JWK parameters
     */
    public Map<String, Object> convertPublicKeyToJWK() {
        String x = toBase64Url(((ECPublicKey) keyPair.getPublic()).getW().getAffineX());
        String y = toBase64Url(((ECPublicKey) keyPair.getPublic()).getW().getAffineY());
        String d = toBase64Url(((ECPrivateKey) keyPair.getPrivate()).getS());
        String kid = getKidFromPublicKey();

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
     * @param alias        New key alias
     */
    public void rotateAndPersistKeyPair(String keystorePath, String password, String alias) {
        try {
            // Generate new EC key pair
            KeyPair newKeyPair = generateEcKeyPair();

            // Create a self-signed certificate for the key pair
            Certificate cert = SelfSignedCertGenerator.generate("CN=DidKey", newKeyPair);

            // Load or create keystore
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            java.io.File file = new java.io.File(keystorePath);
            if (file.exists()) {
                try (InputStream is = new java.io.FileInputStream(file)) {
                    keystore.load(is, password.toCharArray());
                }
            } else {
                keystore.load(null, null);
            }

            // Store new key and certificate
            keystore.setKeyEntry(alias, newKeyPair.getPrivate(), password.toCharArray(), new Certificate[]{cert});

            // Save keystore
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                keystore.store(fos, password.toCharArray());
            }

            // Update cached keyPair
            this.keyPair = newKeyPair;
        } catch (Exception e) {
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
     * @return The new key alias
     */
    @Transactional
    public String rotateKeyAndUpdateMetadata(String keystorePath, String password) {
        // Generate a unique alias for the new key
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String alias = "dsptrueconnector-" + timestamp + "-" + randomUUID().toString().substring(0, 8);

        // Rotate and persist the new key
        rotateAndPersistKeyPair(keystorePath, password, alias);

        // Update key metadata in MongoDB
        keyMetadataService.saveNewKeyMetadata(alias);

        return alias;
    }
}
