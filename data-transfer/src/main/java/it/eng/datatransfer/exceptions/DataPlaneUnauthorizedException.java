package it.eng.datatransfer.exceptions;

/**
 * Thrown when a caller presents an API key that does not match the stored key hash for the
 * targeted Data Plane registration.
 */
public class DataPlaneUnauthorizedException extends RuntimeException {

    /**
     * Constructs the exception with a detail message.
     *
     * @param message the detail message
     */
    public DataPlaneUnauthorizedException(String message) {
        super(message);
    }
}
