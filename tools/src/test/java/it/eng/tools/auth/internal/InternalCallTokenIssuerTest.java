package it.eng.tools.auth.internal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InternalCallTokenIssuer}.
 */
class InternalCallTokenIssuerTest {

    private static final String TENANT_ID = "tenant-a";
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final InternalCallTokenIssuer issuer = new InternalCallTokenIssuer(Clock.fixed(NOW, ZoneOffset.UTC));

    private Logger issuerLogger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureLogs() {
        issuerLogger = (Logger) LoggerFactory.getLogger(InternalCallTokenIssuer.class);
        previousLevel = issuerLogger.getLevel();
        issuerLogger.setLevel(Level.TRACE);
        appender = new ListAppender<>();
        appender.start();
        issuerLogger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        issuerLogger.detachAppender(appender);
        issuerLogger.setLevel(previousLevel);
    }

    @Test
    @DisplayName("A token grants ROLE_INTERNAL only and carries the fixed subject and issuer")
    void tokenGrantsInternalRoleOnly() {
        DecodedJWT jwt = JWT.decode(issuer.issue(TENANT_ID));

        assertEquals(List.of(InternalCallTokenIssuer.ROLE_INTERNAL),
                jwt.getClaim(InternalCallTokenIssuer.ROLES_CLAIM).asList(String.class));
        assertEquals(InternalCallTokenIssuer.SUBJECT, jwt.getSubject());
        assertEquals("urn:true-connector:internal", jwt.getIssuer());
        assertEquals("HS256", jwt.getAlgorithm());
    }

    @Test
    @DisplayName("A token expires 60 seconds after it is issued")
    void tokenExpiresAfterSixtySeconds() {
        DecodedJWT jwt = JWT.decode(issuer.issue(TENANT_ID));

        assertEquals(NOW, jwt.getIssuedAtAsInstant());
        assertEquals(NOW.plusSeconds(60), jwt.getExpiresAtAsInstant());
    }

    @Test
    @DisplayName("A token carries tenant_id when a tenant is given")
    void tokenCarriesTenantId() {
        DecodedJWT jwt = JWT.decode(issuer.issue(TENANT_ID));

        assertEquals(TENANT_ID, jwt.getClaim(InternalCallTokenIssuer.TENANT_ID_CLAIM).asString());
        assertEquals(Optional.of(TENANT_ID), issuer.verify(issuer.issue(TENANT_ID)).flatMap(InternalCallClaims::tenantId));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("A token has no tenant_id claim when no tenant is given")
    void tokenHasNoTenantIdWithoutTenant(String tenantId) {
        String token = issuer.issue(tenantId);

        assertTrue(JWT.decode(token).getClaim(InternalCallTokenIssuer.TENANT_ID_CLAIM).isMissing());
        InternalCallClaims claims = issuer.verify(token).orElseThrow();
        assertTrue(claims.tenantId().isEmpty());
        assertTrue(claims.hasInternalRole());
    }

    @Test
    @DisplayName("authorizationHeader formats a fresh token as a Bearer header")
    void authorizationHeaderIsBearer() {
        String header = issuer.authorizationHeader(TENANT_ID);

        assertTrue(header.startsWith("Bearer "));
        assertTrue(issuer.verify(header.substring("Bearer ".length())).isPresent());
    }

    @Test
    @DisplayName("Each call mints a new token reflecting its tenant")
    void eachCallMintsNewToken() {
        assertNotEquals(issuer.issue("tenant-a"), issuer.issue("tenant-b"));
    }

    @Test
    @DisplayName("A token verifies on the instance that signed it")
    void tokenVerifiesOnSameInstance() {
        InternalCallClaims claims = issuer.verify(issuer.issue(TENANT_ID)).orElseThrow();

        assertEquals(List.of(InternalCallTokenIssuer.ROLE_INTERNAL), claims.roles());
        assertTrue(claims.hasInternalRole());
    }

    @Test
    @DisplayName("A token from one issuer instance fails verification on another")
    void tokenFromAnotherInstanceIsRejected() {
        InternalCallTokenIssuer other = new InternalCallTokenIssuer(Clock.fixed(NOW, ZoneOffset.UTC));

        assertTrue(other.verify(issuer.issue(TENANT_ID)).isEmpty());
        assertTrue(issuer.verify(other.issue(TENANT_ID)).isEmpty());
    }

    @Test
    @DisplayName("A token with a wrong issuer is rejected")
    void wrongIssuerIsRejected() {
        String token = issuer.sign("urn:someone-else", List.of(InternalCallTokenIssuer.ROLE_INTERNAL), TENANT_ID, NOW);

        assertTrue(issuer.verify(token).isEmpty());
    }

    @Test
    @DisplayName("A token is accepted within the 5 second clock skew after expiry")
    void tokenAcceptedWithinClockSkew() {
        String token = issuer.sign(InternalCallTokenIssuer.ISSUER, List.of(InternalCallTokenIssuer.ROLE_INTERNAL),
                TENANT_ID, NOW.minusSeconds(60 + 4));

        assertTrue(issuer.verify(token).isPresent());
    }

    @Test
    @DisplayName("A token expired beyond the 5 second clock skew is rejected")
    void expiredTokenIsRejected() {
        String token = issuer.sign(InternalCallTokenIssuer.ISSUER, List.of(InternalCallTokenIssuer.ROLE_INTERNAL),
                TENANT_ID, NOW.minus(Duration.ofSeconds(60 + 6)));

        assertTrue(issuer.verify(token).isEmpty());
    }

    @Test
    @DisplayName("A token without exp is rejected")
    void tokenWithoutExpiryIsRejected() {
        String token = JWT.create()
                .withIssuer(InternalCallTokenIssuer.ISSUER)
                .withClaim(InternalCallTokenIssuer.ROLES_CLAIM, List.of(InternalCallTokenIssuer.ROLE_INTERNAL))
                .sign(Algorithm.none());

        assertTrue(issuer.verify(token).isEmpty());
    }

    @Test
    @DisplayName("A tampered or malformed token is rejected")
    void tamperedTokenIsRejected() {
        String token = issuer.issue(TENANT_ID);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertTrue(issuer.verify(tampered).isEmpty());
        assertTrue(issuer.verify("not-a-jwt").isEmpty());
        assertTrue(issuer.verify(null).isEmpty());
        assertTrue(issuer.verify("").isEmpty());
    }

    @Test
    @DisplayName("A validly signed token without ROLE_INTERNAL verifies but grants no internal role")
    void tokenWithoutInternalRole() {
        String token = issuer.sign(InternalCallTokenIssuer.ISSUER, List.of("ROLE_ADMIN"), null, NOW);

        InternalCallClaims claims = issuer.verify(token).orElseThrow();
        assertFalse(claims.hasInternalRole());
    }

    @Test
    @DisplayName("Neither the key nor a token appears in toString or in logs")
    void keyAndTokenNeverExposed() {
        String token = issuer.issue(TENANT_ID);
        issuer.verify(token);
        issuer.verify(token + "x");
        InternalCallClaims claims = issuer.verify(token).orElseThrow();
        String signature = token.substring(token.lastIndexOf('.') + 1);

        assertFalse(issuer.toString().contains(token));
        assertFalse(issuer.toString().contains(signature));
        assertFalse(claims.toString().contains(token));
        assertFalse(claims.toString().contains(signature));
        assertFalse(appender.list.isEmpty(), "The rejected token should produce a debug log line");
        appender.list.forEach(event -> {
            assertFalse(event.getFormattedMessage().contains(signature));
            assertFalse(event.getFormattedMessage().contains(token.substring(0, token.indexOf('.'))));
        });
    }
}
