package it.eng.dcp.common.exception;

/**
 * Raised when a DCP endpoint does not use HTTPS and {@code dcp.allow-insecure-http} is not enabled.
 */
public class InsecureEndpointException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message description of the rejected endpoint
     */
    public InsecureEndpointException(String message) {
        super(message);
    }
}
