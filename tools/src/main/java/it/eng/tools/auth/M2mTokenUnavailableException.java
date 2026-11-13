package it.eng.tools.auth;

/**
 * Thrown when the connector cannot obtain the access token for an outbound machine-to-machine call, for
 * example because the token endpoint rejected the client credentials or could not be resolved.
 * <p>
 * The message may name the token endpoint, the HTTP status and the OAuth {@code error} code. It never
 * contains a client secret, a token or a response body.
 */
public class M2mTokenUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the detail message, without secrets, tokens or response content
     */
    public M2mTokenUnavailableException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message the detail message, without secrets, tokens or response content
     * @param cause the cause
     */
    public M2mTokenUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
