package it.eng.tools.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import it.eng.tools.auth.condition.InternalAuthenticationModeCondition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Issues and validates HMAC-SHA256 signed JWT access/refresh token pairs for the unified
 * {@code /api/v1/auth/*} contract's {@code INTERNAL} authentication mode.
 *
 * <p>This class has no dependency on any {@code connector}-module user type: callers pass the
 * subject, email, role names, tenant identifier, and any additional claims explicitly, so new
 * claims can be introduced at the call site without changing this class.
 *
 * <p><b>Access vs. refresh tokens</b>: both tokens share the same claim shape ({@code sub},
 * {@code email}, {@code roles}, {@code tenantId}, {@code iat}, {@code exp}, {@code jti}, plus any
 * caller-supplied extra claims). Refresh tokens additionally carry a {@value #TOKEN_TYPE_CLAIM}
 * claim set to {@value #REFRESH_TOKEN_TYPE}; access tokens carry no {@value #TOKEN_TYPE_CLAIM}
 * claim. Callers validating a token for use as an access token must reject any decoded token that
 * carries a {@value #REFRESH_TOKEN_TYPE} {@value #TOKEN_TYPE_CLAIM} claim, so a refresh token
 * cannot be replayed against a protected endpoint.
 *
 * <p>The signing secret is never logged, including at {@code DEBUG} level.
 */
@Slf4j
@Component
@Conditional(InternalAuthenticationModeCondition.class)
public class JwtService {

    /** Claim name for the subject's email address. */
    public static final String EMAIL_CLAIM = "email";

    /** Claim name for the subject's role names. */
    public static final String ROLES_CLAIM = "roles";

    /** Claim name for the subject's tenant identifier. */
    public static final String TENANT_ID_CLAIM = "tenantId";

    /** Claim name distinguishing refresh tokens from access tokens. */
    public static final String TOKEN_TYPE_CLAIM = "token_type";

    /** {@link #TOKEN_TYPE_CLAIM} value carried by refresh tokens only. */
    public static final String REFRESH_TOKEN_TYPE = "refresh";

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties jwtProperties;

    /**
     * Creates a new {@link JwtService}.
     *
     * @param jwtProperties the JWT signing configuration
     */
    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * Validates the configured signing secret at application startup.
     *
     * @throws IllegalStateException if {@code application.security.jwt.secret} is missing or
     *                                decodes to fewer than {@value #MIN_SECRET_BYTES} bytes
     *                                (256 bits) of raw UTF-8 data
     */
    @PostConstruct
    public void init() {
        int secretBytes = secretByteLength();
        if (secretBytes < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "Property 'application.security.jwt.secret' must be configured with at least "
                            + MIN_SECRET_BYTES + " bytes (256 bits) of raw UTF-8 data; found "
                            + secretBytes + " byte(s).");
        }
        log.info("JwtService initialized with access TTL {}ms and refresh TTL {}ms",
                jwtProperties.getAccessExpirationMs(), jwtProperties.getRefreshExpirationMs());
    }

    /**
     * Issues a new access/refresh token pair for the given subject.
     *
     * @param subject     the subject (user id) embedded as the {@code sub} claim
     * @param email       the subject's email address, embedded as the {@value #EMAIL_CLAIM} claim
     * @param roles       the subject's role names, embedded as the {@value #ROLES_CLAIM} claim
     * @param tenantId    the subject's tenant identifier, embedded as the
     *                    {@value #TENANT_ID_CLAIM} claim
     * @param extraClaims additional claims merged into both tokens; may be {@code null} or empty
     * @return the issued {@link TokenPair}
     */
    public TokenPair issueTokenPair(String subject, String email, List<String> roles, String tenantId,
            Map<String, Object> extraClaims) {
        Algorithm algorithm = signingAlgorithm();
        Instant now = Instant.now();
        Instant accessExpiry = now.plusMillis(jwtProperties.getAccessExpirationMs());
        Instant refreshExpiry = now.plusMillis(jwtProperties.getRefreshExpirationMs());

        String accessToken = baseTokenBuilder(subject, email, roles, tenantId, now, accessExpiry, extraClaims)
                .sign(algorithm);
        String refreshToken = baseTokenBuilder(subject, email, roles, tenantId, now, refreshExpiry, extraClaims)
                .withClaim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .sign(algorithm);

        long accessExpiresInSeconds = jwtProperties.getAccessExpirationMs() / 1000L;
        return new TokenPair(accessToken, refreshToken, accessExpiresInSeconds);
    }

    /**
     * Verifies the signature and expiry of the given token and returns its decoded claims.
     *
     * @param token the compact JWT string to verify
     * @return the decoded, signature- and expiry-verified token
     * @throws JWTVerificationException if the token is expired, tampered with, malformed, or
     *                                    signed with a different secret
     */
    public DecodedJWT verifyAndDecode(String token) {
        return JWT.require(signingAlgorithm()).build().verify(token);
    }

    // Builds the shared set of claims common to both access and refresh tokens.
    private JWTCreator.Builder baseTokenBuilder(String subject, String email, List<String> roles, String tenantId,
            Instant issuedAt, Instant expiresAt, Map<String, Object> extraClaims) {
        JWTCreator.Builder builder = JWT.create()
                .withSubject(subject)
                .withClaim(EMAIL_CLAIM, email)
                .withClaim(ROLES_CLAIM, roles)
                .withClaim(TENANT_ID_CLAIM, tenantId)
                .withIssuedAt(Date.from(issuedAt))
                .withExpiresAt(Date.from(expiresAt))
                .withJWTId(UUID.randomUUID().toString());
        if (extraClaims != null && !extraClaims.isEmpty()) {
            // Merges caller-supplied claims without requiring any change to this class.
            builder.withPayload(extraClaims);
        }
        return builder;
    }

    // Computed on demand rather than cached, so a corrected secret takes effect without a restart
    // being the only recovery path from a startup failure, and so the secret is never held longer
    // than necessary in a dedicated field.
    private Algorithm signingAlgorithm() {
        return Algorithm.HMAC256(jwtProperties.getSecret());
    }

    private int secretByteLength() {
        String secret = jwtProperties.getSecret();
        return secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
    }
}
