package it.eng.connector.configuration;

import it.eng.tools.auth.keycloak.KeycloakTrustProperties;
import it.eng.tools.auth.keycloak.issuer.IssuerType;

import java.util.Objects;
import java.util.Optional;

/**
 * Tells the issuer type and realm of a token whose {@code iss} the trusted-issuer registry has already verified.
 *
 * <p>The {@code platform} and {@code connector-admin} issuers are compared exactly with the issuer URLs that
 * {@link KeycloakTrustProperties#issuerFor(String)} builds. Any other verified issuer is a tenant realm: the
 * registry's decoders accept no other issuer, so the tenant registry is not queried a second time. Only use
 * this class with an {@code iss} from a token that a registry decoder accepted, or whose signature it verified.
 */
final class KeycloakIssuerClassifier {

    private final KeycloakTrustProperties properties;
    private final String platformIssuer;
    private final String adminIssuer;

    /**
     * Creates the classifier.
     *
     * @param properties the Keycloak trust configuration, which names the platform and admin realms
     * @throws NullPointerException when {@code properties} is {@code null}
     */
    KeycloakIssuerClassifier(KeycloakTrustProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.platformIssuer = properties.issuerFor(properties.getPlatformRealm());
        this.adminIssuer = properties.issuerFor(properties.getAdminRealm());
    }

    /**
     * Returns the issuer type of a verified issuer.
     *
     * @param verifiedIssuer the verified {@code iss} of a token, may be {@code null}
     * @return the platform or admin type for those exact issuers, the tenant type for any other issuer, and
     *         empty when {@code verifiedIssuer} is {@code null}
     */
    Optional<IssuerType> typeOf(String verifiedIssuer) {
        if (verifiedIssuer == null) {
            return Optional.empty();
        }
        if (platformIssuer.equals(verifiedIssuer)) {
            return Optional.of(IssuerType.PLATFORM);
        }
        if (adminIssuer.equals(verifiedIssuer)) {
            return Optional.of(IssuerType.ADMIN);
        }
        return Optional.of(IssuerType.TENANT);
    }

    /**
     * Returns the realm name of a verified issuer.
     *
     * @param verifiedIssuer the verified {@code iss} of a token, may be {@code null}
     * @return the realm, or empty when {@code verifiedIssuer} is not {@code <base-url>/realms/<realm>} for a
     *         valid realm name
     */
    Optional<String> realmOf(String verifiedIssuer) {
        if (verifiedIssuer == null) {
            return Optional.empty();
        }
        String candidate = verifiedIssuer.substring(verifiedIssuer.lastIndexOf('/') + 1);
        try {
            return properties.issuerFor(candidate).equals(verifiedIssuer) ? Optional.of(candidate) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
