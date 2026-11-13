package it.eng.tools.auth.keycloak;

/**
 * The parts of an OpenID discovery document that the connector uses.
 *
 * @param issuer the issuer, equal to the issuer the document was requested for
 * @param jwksUri the JWKS URI, never {@code null}
 * @param tokenEndpoint the token endpoint, may be {@code null} when the document does not declare one
 */
public record DiscoveryDocument(String issuer, String jwksUri, String tokenEndpoint) {
}
