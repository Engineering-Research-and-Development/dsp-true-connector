package it.eng.dcp.core;

/**
 * Exception raised when DID resolution fails or the DID document is malformed/incomplete.
 */
public class DidResolutionException extends Exception {

    public DidResolutionException(String message) {
        super(message);
    }

    public DidResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

