package it.eng.tools.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

/**
 * Utility for creating signed JWT tokens in tests.
 */
public class JwtTokenTestUtils {

    private static final RSAPublicKey PUBLIC_KEY;
    private static final RSAPrivateKey PRIVATE_KEY;

    static {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            PUBLIC_KEY = (RSAPublicKey) kp.getPublic();
            PRIVATE_KEY = (RSAPrivateKey) kp.getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private JwtTokenTestUtils() {
    }

    /**
     * Creates a signed JWT token that expires in 5 seconds.
     *
     * @return a compact JWT string
     */
    public static String createTestToken() {
        Algorithm algorithm = Algorithm.RSA256(PUBLIC_KEY, PRIVATE_KEY);
        return JWT.create()
                .withIssuer("Test")
                .withSubject("Test Details")
                .withClaim("userId", "1234")
                .withIssuedAt(new Date())
                .withKeyId("NkJCQzIyQzRBMEU4NjhGNUU4MzU4RkY0M0ZDQzkwOUQ0Q0VGNUMwQg")
                .withExpiresAt(new Date(System.currentTimeMillis() + 5000L))
                .withJWTId(UUID.randomUUID().toString())
                .sign(algorithm);
    }
}
