package it.eng.tools.auth.keycloak;

/**
 * Thrown when the OpenID discovery document of a Keycloak issuer cannot be loaded or is not acceptable.
 * The message names the issuer but never contains response content.
 */
public class KeycloakDiscoveryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the detail message, without response content
     */
    public KeycloakDiscoveryException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message the detail message, without response content
     * @param cause the cause
     */
    public KeycloakDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
