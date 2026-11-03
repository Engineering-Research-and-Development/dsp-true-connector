package it.eng.tools.auth.keycloak.issuer;

import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

/**
 * Per-issuer token checks that run after the issuer's cached decoder, for use with the
 * additional-validator overload of {@link TrustedIssuerRegistry#issuerSelectingDecoder}.
 * <p>
 * Each issuer type has its own rules:
 * <table>
 *   <caption>Rules per issuer type</caption>
 *   <tr><th>Type</th><th>{@code aud}</th><th>{@code azp}</th><th>Own privileged role</th><th>{@code tenant_id}</th></tr>
 *   <tr><td>{@link IssuerType#TENANT}</td><td>contains the audience</td><td>the UI client id</td>
 *       <td>{@code ADMIN}</td><td>required, equals {@link TrustedIssuer#tenantId()}</td></tr>
 *   <tr><td>{@link IssuerType#ADMIN}</td><td>contains the audience</td><td>the UI client id</td>
 *       <td>{@code SUPER_ADMIN}</td><td>absent</td></tr>
 *   <tr><td>{@link IssuerType#PLATFORM}</td><td>contains the audience</td><td>the platform client id</td>
 *       <td>{@code CONNECTOR}, required (exact match)</td><td>absent</td></tr>
 * </table>
 * <p>
 * The privileged realm roles are {@code ADMIN}, {@code SUPER_ADMIN}, {@code CONNECTOR} and {@code INTERNAL}, read from {@code realm_access.roles} and
 * compared case-insensitively. A token carrying a privileged role other than its realm's own role is rejected as
 * a whole instead of having the role dropped, because a realm that issues it is misconfigured or compromised.
 * That failure carries the error code {@link #REALM_ROLE_VIOLATION}; every other failure carries
 * {@code invalid_token}. Non-privileged roles are ignored. A tenant or admin token without its own role is still
 * valid here; authorization decides later.
 * <p>
 * {@code tenant_id} must be a string claim; an empty string counts as missing. Error descriptions name the failed
 * rule and never the claim values.
 */
public final class IssuerTokenValidators {

    /**
     * Error code of a token that carries a privileged realm role its issuer may not grant. Lets callers tell a
     * realm role violation apart from other validation failures, which use {@code invalid_token}.
     */
    public static final String REALM_ROLE_VIOLATION = "realm_role_violation";

    /**
     * The privileged realm roles, in upper case.
     */
    static final Set<String> PRIVILEGED_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "CONNECTOR", "INTERNAL");

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String AUTHORIZED_PARTY_CLAIM = "azp";
    private static final String TENANT_ID_CLAIM = "tenant_id";
    private static final String TENANT_ROLE = "ADMIN";
    private static final String ADMIN_ROLE = "SUPER_ADMIN";
    private static final String CONNECTOR_ROLE = "CONNECTOR";

    private static final OAuth2TokenValidatorResult TENANT_ID_MISMATCH = invalid(
            "The tenant_id claim is missing or does not match the tenant bound to the issuer");
    private static final OAuth2TokenValidatorResult TENANT_ID_PRESENT = invalid(
            "The tenant_id claim is not allowed for this issuer");

    private final OAuth2TokenValidator<Jwt> audienceValidator;
    private final OAuth2TokenValidator<Jwt> uiAuthorizedPartyValidator;
    private final OAuth2TokenValidator<Jwt> adminValidator;
    private final OAuth2TokenValidator<Jwt> platformValidator;

    /**
     * Creates the validators.
     *
     * @param audience the audience every accepted token must carry in {@code aud}
     * @param platformClientId the client id a platform token must carry in {@code azp}
     * @param uiClientId the client id a tenant or connector-admin token must carry in {@code azp}
     * @throws NullPointerException when an argument is {@code null}
     * @throws IllegalArgumentException when an argument is blank
     */
    public IssuerTokenValidators(String audience, String platformClientId, String uiClientId) {
        requireText(audience, "audience");
        requireText(platformClientId, "platformClientId");
        requireText(uiClientId, "uiClientId");
        this.audienceValidator = audienceValidator(audience);
        this.uiAuthorizedPartyValidator = authorizedPartyValidator(uiClientId);
        this.adminValidator = new DelegatingOAuth2TokenValidator<>(List.of(
                realmRoleValidator(ADMIN_ROLE),
                audienceValidator,
                uiAuthorizedPartyValidator,
                IssuerTokenValidators::rejectTenantId));
        this.platformValidator = new DelegatingOAuth2TokenValidator<>(List.of(
                realmRoleValidator(CONNECTOR_ROLE),
                audienceValidator,
                authorizedPartyValidator(platformClientId),
                IssuerTokenValidators::requireConnectorRole,
                IssuerTokenValidators::rejectTenantId));
    }

    /**
     * Returns the validator for tokens of the given issuer. The realm role rule runs first, so a realm role
     * violation is the first error of a failed result.
     *
     * @param issuer the resolved issuer of the token
     * @return the rules of the issuer's type, see the class description
     */
    public OAuth2TokenValidator<Jwt> forIssuer(TrustedIssuer issuer) {
        return switch (issuer.type()) {
            case TENANT -> tenantValidator(issuer.tenantId());
            case ADMIN -> adminValidator;
            case PLATFORM -> platformValidator;
        };
    }

    private OAuth2TokenValidator<Jwt> tenantValidator(String tenantId) {
        return new DelegatingOAuth2TokenValidator<>(List.of(
                realmRoleValidator(TENANT_ROLE),
                audienceValidator,
                uiAuthorizedPartyValidator,
                tenantIdValidator(tenantId)));
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static OAuth2TokenValidatorResult invalid(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        OAuth2TokenValidatorResult failure = invalid("The required audience is missing");
        return jwt -> {
            List<String> tokenAudience = jwt.getAudience();
            return tokenAudience != null && tokenAudience.contains(audience)
                    ? OAuth2TokenValidatorResult.success()
                    : failure;
        };
    }

    private static OAuth2TokenValidator<Jwt> authorizedPartyValidator(String clientId) {
        OAuth2TokenValidatorResult failure = invalid("The authorized party is not accepted");
        return jwt -> jwt.getClaims().get(AUTHORIZED_PARTY_CLAIM) instanceof String azp && clientId.equals(azp)
                ? OAuth2TokenValidatorResult.success()
                : failure;
    }

    private static OAuth2TokenValidator<Jwt> realmRoleValidator(String ownRole) {
        OAuth2TokenValidatorResult failure = OAuth2TokenValidatorResult.failure(new OAuth2Error(REALM_ROLE_VIOLATION,
                "The token carries a privileged realm role its issuer may not grant", null));
        return jwt -> {
            boolean violation = realmRoles(jwt).stream()
                    .filter(String.class::isInstance)
                    .map(role -> ((String) role).toUpperCase(Locale.ROOT))
                    .anyMatch(role -> PRIVILEGED_ROLES.contains(role) && !ownRole.equals(role));
            return violation ? failure : OAuth2TokenValidatorResult.success();
        };
    }

    private static OAuth2TokenValidatorResult requireConnectorRole(Jwt jwt) {
        return realmRoles(jwt).contains(CONNECTOR_ROLE)
                ? OAuth2TokenValidatorResult.success()
                : invalid("The token does not carry the required realm role");
    }

    private static OAuth2TokenValidator<Jwt> tenantIdValidator(String tenantId) {
        return jwt -> tenantId.equals(tenantId(jwt)) ? OAuth2TokenValidatorResult.success() : TENANT_ID_MISMATCH;
    }

    private static OAuth2TokenValidatorResult rejectTenantId(Jwt jwt) {
        Object claim = jwt.getClaims().get(TENANT_ID_CLAIM);
        boolean absent = claim == null || (claim instanceof String value && value.isEmpty());
        return absent ? OAuth2TokenValidatorResult.success() : TENANT_ID_PRESENT;
    }

    private static String tenantId(Jwt jwt) {
        return jwt.getClaims().get(TENANT_ID_CLAIM) instanceof String value && !value.isEmpty() ? value : null;
    }

    private static Collection<?> realmRoles(Jwt jwt) {
        return jwt.getClaims().get(REALM_ACCESS_CLAIM) instanceof Map<?, ?> realmAccess
                && realmAccess.get(ROLES_CLAIM) instanceof Collection<?> roles
                ? roles
                : List.of();
    }
}
