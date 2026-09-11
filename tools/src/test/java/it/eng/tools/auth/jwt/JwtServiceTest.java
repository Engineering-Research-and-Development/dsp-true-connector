package it.eng.tools.auth.jwt;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JwtService}.
 */
class JwtServiceTest {

    private static final String VALID_SECRET = "unit-test-secret-value-with-at-least-256-bits!!";
    private static final String OTHER_SECRET = "a-completely-different-unit-test-secret-32bytes";

    private JwtProperties jwtProperties;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(VALID_SECRET);
        jwtProperties.setAccessExpirationMs(900_000L);
        jwtProperties.setRefreshExpirationMs(604_800_000L);
        jwtService = new JwtService(jwtProperties);
        jwtService.init();
    }

    @Test
    @DisplayName("Should issue and verify an access/refresh token pair round-trip")
    void issueAndVerifyRoundTrip() {
        TokenPair tokenPair = jwtService.issueTokenPair("user-1", "user@example.com",
                List.of("ROLE_ADMIN"), "tenant-a", Map.of());

        DecodedJWT accessClaims = jwtService.verifyAndDecode(tokenPair.accessToken());
        assertEquals("user-1", accessClaims.getSubject());
        assertEquals("user@example.com", accessClaims.getClaim(JwtService.EMAIL_CLAIM).asString());
        assertEquals(List.of("ROLE_ADMIN"), accessClaims.getClaim(JwtService.ROLES_CLAIM).asList(String.class));
        assertEquals("tenant-a", accessClaims.getClaim(JwtService.TENANT_ID_CLAIM).asString());
        assertTrue(accessClaims.getClaim(JwtService.TOKEN_TYPE_CLAIM).isMissing());
        assertNotNull(accessClaims.getIssuedAt());
        assertNotNull(accessClaims.getExpiresAt());
        assertNotNull(accessClaims.getId());

        DecodedJWT refreshClaims = jwtService.verifyAndDecode(tokenPair.refreshToken());
        assertEquals("user-1", refreshClaims.getSubject());
        assertEquals(JwtService.REFRESH_TOKEN_TYPE,
                refreshClaims.getClaim(JwtService.TOKEN_TYPE_CLAIM).asString());
        assertEquals(900_000L / 1000L, tokenPair.accessExpiresInSeconds());
    }

    @Test
    @DisplayName("Should propagate arbitrary extraClaims entries without requiring JwtService changes")
    void extraClaimsArePropagated() {
        TokenPair tokenPair = jwtService.issueTokenPair("user-1", "user@example.com",
                List.of("ROLE_ADMIN"), "tenant-a", Map.of("newFeatureFlag", "enabled", "loginCount", 3));

        DecodedJWT decoded = jwtService.verifyAndDecode(tokenPair.accessToken());
        assertEquals("enabled", decoded.getClaim("newFeatureFlag").asString());
        assertEquals(3, decoded.getClaim("loginCount").asInt());
    }

    @Test
    @DisplayName("Should fail on missing required fields")
    void missingRequiredFields() {
        assertThrows(NullPointerException.class, () -> jwtService.issueTokenPair(null, "user@example.com",
                List.of("ROLE_ADMIN"), "tenant-a", Map.of()));
        assertThrows(NullPointerException.class, () -> jwtService.issueTokenPair("user-1", null,
                List.of("ROLE_ADMIN"), "tenant-a", Map.of()));
        assertThrows(NullPointerException.class, () -> jwtService.issueTokenPair("user-1", "user@example.com",
                null, "tenant-a", Map.of()));
    }

    @Test
    @DisplayName("Should reject extraClaims that collide with a reserved claim name")
    void extraClaimsCollidingWithReservedNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> jwtService.issueTokenPair("user-1",
                "user@example.com", List.of("ROLE_ADMIN"), "tenant-a", Map.of(JwtService.ROLES_CLAIM,
                        List.of("ROLE_SUPER_ADMIN"))));
        assertThrows(IllegalArgumentException.class, () -> jwtService.issueTokenPair("user-1",
                "user@example.com", List.of("ROLE_ADMIN"), "tenant-a", Map.of("exp", 9_999_999_999L)));
        assertThrows(IllegalArgumentException.class, () -> jwtService.issueTokenPair("user-1",
                "user@example.com", List.of("ROLE_ADMIN"), "tenant-a",
                Map.of(JwtService.TOKEN_TYPE_CLAIM, JwtService.REFRESH_TOKEN_TYPE)));
    }

    @Test
    @DisplayName("Should reject an expired token with a clear exception")
    void expiredTokenIsRejected() {
        jwtProperties.setAccessExpirationMs(-1_000L);
        TokenPair tokenPair = jwtService.issueTokenPair("user-1", "user@example.com",
                List.of("ROLE_ADMIN"), "tenant-a", Map.of());

        assertThrows(TokenExpiredException.class, () -> jwtService.verifyAndDecode(tokenPair.accessToken()));
    }

    @Test
    @DisplayName("Should reject a token signed with a different secret")
    void tokenSignedWithDifferentSecretIsRejected() {
        String foreignToken = JWT.create()
                .withSubject("user-1")
                .sign(Algorithm.HMAC256(OTHER_SECRET));

        assertThrows(SignatureVerificationException.class, () -> jwtService.verifyAndDecode(foreignToken));
    }

    @Test
    @DisplayName("Should fail fast at startup when the configured secret is shorter than 256 bits")
    void undersizedSecretFailsStartup() {
        JwtProperties shortSecretProperties = new JwtProperties();
        shortSecretProperties.setSecret("too-short-secret");
        JwtService shortSecretService = new JwtService(shortSecretProperties);

        IllegalStateException exception = assertThrows(IllegalStateException.class, shortSecretService::init);
        assertTrue(exception.getMessage().contains("application.security.jwt.secret"));
    }

    @Test
    @DisplayName("Should fail fast at startup when the configured secret is missing")
    void missingSecretFailsStartup() {
        JwtProperties missingSecretProperties = new JwtProperties();
        JwtService missingSecretService = new JwtService(missingSecretProperties);

        assertThrows(IllegalStateException.class, missingSecretService::init);
    }
}
