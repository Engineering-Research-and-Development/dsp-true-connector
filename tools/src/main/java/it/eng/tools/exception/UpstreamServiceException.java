package it.eng.tools.exception;

/**
 * Thrown when a service the connector depends on, such as Keycloak, fails or cannot be reached: a 5xx or other
 * unexpected status, or an I/O failure. Mapped to 502 Bad Gateway.
 */
public class UpstreamServiceException extends RuntimeException {

    private static final long serialVersionUID = -4469617104598516723L;

    /**
     * Creates the exception.
     *
     * @param message a generic message; must not contain secrets or the upstream response body
     */
    public UpstreamServiceException(String message) {
        super(message);
    }
}
