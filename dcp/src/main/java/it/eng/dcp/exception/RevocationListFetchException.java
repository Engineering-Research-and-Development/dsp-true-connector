package it.eng.dcp.exception;

/**
 * Exception thrown when a revocation list cannot be fetched from a remote source.
 */
public class RevocationListFetchException extends RuntimeException {
    public RevocationListFetchException(String message) {
        super(message);
    }

    public RevocationListFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}

