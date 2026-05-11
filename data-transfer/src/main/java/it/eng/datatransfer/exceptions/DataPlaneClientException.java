package it.eng.datatransfer.exceptions;

import java.io.Serial;

/**
 * Thrown when an HTTP communication failure occurs while sending a message to a Data Plane service.
 *
 * <p>This is an unchecked exception wrapping low-level {@link java.io.IOException} failures
 * so that callers can detect delivery failures without checked exception handling.</p>
 */
public class DataPlaneClientException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public DataPlaneClientException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new exception with the given message.
     *
     * @param message the detail message
     */
    public DataPlaneClientException(String message) {
        super(message);
    }
}
