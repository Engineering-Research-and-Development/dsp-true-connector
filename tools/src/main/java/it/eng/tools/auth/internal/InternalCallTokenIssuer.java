package it.eng.tools.auth.internal;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.RegisteredClaims;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Mints and verifies the short-lived tokens that authenticate the connector's loopback calls to its own
 * {@code /internal/**} endpoints.
 *
 * <p>The HMAC-SHA256 key is 256 bits, generated with {@link SecureRandom} when the bean is created and
 * held in memory only: it is never configured, logged, serialized or persisted, so a token can be
 * verified only by the process that signed it. Every loopback call targets {@code localhost}, which
 * keeps this correct with several replicas.
 *
 * <p>A token carries {@code sub} {@value #SUBJECT}, {@code iss} {@value #ISSUER}, {@code iat},
 * {@code exp} 60 seconds later, the {@value #ROLES_CLAIM} claim holding {@value #ROLE_INTERNAL} only,
 * and {@value #TENANT_ID_CLAIM} when the call runs for a tenant. Each call mints a new token; nothing is
 * cached. The issuer is independent of the authentication mode.
 *
 * <p>Tokens and the key never appear in logs, exception messages or {@link #toString()}.
 */
@Slf4j
@Component
public class InternalCallTokenIssuer {

    /** Subject of every internal-call token. */
    public static final String SUBJECT = "internal-call";

    /** Issuer of every internal-call token. */
    public static final String ISSUER = "urn:true-connector:internal";

    /** The only authority an internal-call token grants. */
    public static final String ROLE_INTERNAL = "ROLE_INTERNAL";

    /** Claim holding the granted authorities. */
    public static final String ROLES_CLAIM = "roles";

    /** Claim holding the tenant the call runs for; absent when no tenant context is set. */
    public static final String TENANT_ID_CLAIM = "tenant_id";

    /** Lifetime of a minted token. */
    static final Duration TOKEN_LIFETIME = Duration.ofSeconds(60);

    /** Clock skew accepted when checking {@code exp}, {@code iat} and {@code nbf}. */
    static final long CLOCK_SKEW_SECONDS = 5;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int KEY_BYTES = 32;

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Clock clock;

    /**
     * Creates the issuer with a new random key and the system clock.
     */
    public InternalCallTokenIssuer() {
        this(Clock.systemUTC());
    }

    /**
     * Creates the issuer with a new random key and the given clock.
     *
     * @param clock the clock used to set and check token times
     */
    InternalCallTokenIssuer(Clock clock) {
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        this.algorithm = Algorithm.HMAC256(key);
        Arrays.fill(key, (byte) 0);
        this.clock = clock;
        // Only BaseVerification exposes build(Clock); JWT.require always returns one.
        Verification verification = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .withClaimPresence(RegisteredClaims.EXPIRES_AT)
                .acceptLeeway(CLOCK_SKEW_SECONDS);
        this.verifier = ((JWTVerifier.BaseVerification) verification).build(clock);
    }

    /**
     * Mints a new internal-call token.
     *
     * @param tenantId the tenant the call runs for; {@code null} or blank adds no {@value #TENANT_ID_CLAIM}
     *                 claim
     * @return the signed token
     */
    public String issue(String tenantId) {
        return sign(ISSUER, List.of(ROLE_INTERNAL), tenantId, clock.instant());
    }

    /**
     * Mints a new internal-call token and formats it for an {@code Authorization} header.
     *
     * @param tenantId the tenant the call runs for; {@code null} or blank adds no {@value #TENANT_ID_CLAIM}
     *                 claim
     * @return {@code Bearer <token>}
     */
    public String authorizationHeader(String tenantId) {
        return BEARER_PREFIX + issue(tenantId);
    }

    /**
     * Verifies a token against the in-memory key: signature, {@code iss} and {@code exp} with
     * {@value #CLOCK_SKEW_SECONDS} seconds of clock skew.
     *
     * @param token the raw token, without the {@code Bearer} prefix
     * @return the verified claims, or empty when the token is missing, malformed, signed with another key,
     *         expired or from another issuer
     */
    public Optional<InternalCallClaims> verify(String token) {
        if (StringUtils.isBlank(token)) {
            return Optional.empty();
        }
        try {
            DecodedJWT jwt = verifier.verify(token);
            List<String> roles = Optional.ofNullable(jwt.getClaim(ROLES_CLAIM).asList(String.class))
                    .orElse(List.of())
                    .stream()
                    .filter(Objects::nonNull)
                    .toList();
            Optional<String> tenantId = Optional.ofNullable(jwt.getClaim(TENANT_ID_CLAIM).asString())
                    .filter(StringUtils::isNotBlank);
            return Optional.of(new InternalCallClaims(roles, tenantId));
        } catch (JWTVerificationException | IllegalArgumentException ex) {
            // The exception type is enough to diagnose; messages are not logged so no claim value leaks.
            log.debug("Internal-call token rejected: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * Signs a token with the in-memory key. Package-private so tests can mint tokens that
     * {@link #verify(String)} must reject or that lack {@value #ROLE_INTERNAL}.
     *
     * @param issuer   the {@code iss} claim
     * @param roles    the {@value #ROLES_CLAIM} claim
     * @param tenantId the {@value #TENANT_ID_CLAIM} claim; omitted when {@code null} or blank
     * @param issuedAt the {@code iat} claim; {@code exp} is {@link #TOKEN_LIFETIME} later
     * @return the signed token
     */
    String sign(String issuer, List<String> roles, String tenantId, Instant issuedAt) {
        JWTCreator.Builder builder = JWT.create()
                .withSubject(SUBJECT)
                .withIssuer(issuer)
                .withIssuedAt(issuedAt)
                .withExpiresAt(issuedAt.plus(TOKEN_LIFETIME))
                .withClaim(ROLES_CLAIM, roles);
        if (StringUtils.isNotBlank(tenantId)) {
            builder.withClaim(TENANT_ID_CLAIM, tenantId);
        }
        return builder.sign(algorithm);
    }

    @Override
    public String toString() {
        return "InternalCallTokenIssuer[iss=" + ISSUER + "]";
    }
}
