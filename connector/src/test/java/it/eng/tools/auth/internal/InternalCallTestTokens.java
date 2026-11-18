package it.eng.tools.auth.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Test-only access to {@link InternalCallTokenIssuer#sign(String, List, String, Instant)}, so connector
 * integration tests can mint internal-call tokens with the running application's in-memory key that the
 * {@code /internal/**} chain must reject or refuse.
 *
 * <p>Lives in the issuer's package because the signing method is package-private.
 */
public final class InternalCallTestTokens {

    private InternalCallTestTokens() {
    }

    /**
     * Mints a token whose {@code exp} lies beyond the accepted clock skew.
     *
     * @param issuer the application's issuer bean
     * @return an expired token
     */
    public static String expired(InternalCallTokenIssuer issuer) {
        Instant issuedAt = Instant.now()
                .minus(InternalCallTokenIssuer.TOKEN_LIFETIME)
                .minus(Duration.ofSeconds(InternalCallTokenIssuer.CLOCK_SKEW_SECONDS + 30));
        return issuer.sign(InternalCallTokenIssuer.ISSUER, List.of(InternalCallTokenIssuer.ROLE_INTERNAL),
                null, issuedAt);
    }

    /**
     * Mints a token signed with the right key but carrying another {@code iss}.
     *
     * @param issuer the application's issuer bean
     * @return a token with a wrong issuer
     */
    public static String wrongIssuer(InternalCallTokenIssuer issuer) {
        return issuer.sign("urn:true-connector:someone-else", List.of(InternalCallTokenIssuer.ROLE_INTERNAL),
                null, Instant.now());
    }

    /**
     * Mints a valid token that grants the given roles instead of {@code ROLE_INTERNAL}.
     *
     * @param issuer the application's issuer bean
     * @param roles  the roles to grant
     * @return a valid token without {@code ROLE_INTERNAL}
     */
    public static String withRoles(InternalCallTokenIssuer issuer, List<String> roles) {
        return issuer.sign(InternalCallTokenIssuer.ISSUER, roles, null, Instant.now());
    }
}
