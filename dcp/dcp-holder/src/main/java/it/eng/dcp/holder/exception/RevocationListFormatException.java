package it.eng.dcp.holder.exception;

/**
 * Exception thrown when a revocation list has an invalid format or cannot be decoded.
 */
public class RevocationListFormatException extends RuntimeException {
    public RevocationListFormatException(String message) {
        super(message);
    }

    public RevocationListFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}

