package it.eng.datatransfer.exceptions;

import java.io.Serial;

/**
 * Thrown when a presigned GET URL returns HTTP 403 (Forbidden / Expired).
 */
public class PresignedUrlExpiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * Constructs a new exception for the given transfer process or URL identifier.
     *
     * @param identifier the MongoDB ID or URL that triggered the expiry
     */
    public PresignedUrlExpiredException(String identifier) {
        super("Presigned URL for transfer " + identifier + " has expired (HTTP 403).");
    }
}
