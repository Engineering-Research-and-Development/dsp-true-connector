package it.eng.tools.auth.keycloak.issuer;

import java.util.Objects;

/**
 * A Keycloak issuer the connector trusts, as resolved by {@link TrustedIssuerRegistry#resolve(String)}.
 *
 * @param issuer the issuer URL, {@code <base-url>/realms/<realm>}
 * @param realm the realm name
 * @param type the kind of issuer
 * @param tenantId the id of the tenant bound to the realm for {@link IssuerType#TENANT}, otherwise {@code null}
 */
public record TrustedIssuer(String issuer, String realm, IssuerType type, String tenantId) {

    /**
     * Validates the components.
     *
     * @throws NullPointerException when {@code issuer}, {@code realm} or {@code type} is {@code null}
     * @throws IllegalArgumentException when {@code tenantId} is missing for a tenant issuer or present for
     *         any other issuer
     */
    public TrustedIssuer {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(realm, "realm");
        Objects.requireNonNull(type, "type");
        if ((type == IssuerType.TENANT) != (tenantId != null)) {
            throw new IllegalArgumentException("tenantId must be set for tenant issuers only");
        }
    }
}
