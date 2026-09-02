package it.eng.tools.exception;

/**
 * Exception thrown when there is an issue with the S3 related operations.
 */
public class S3ServerException extends RuntimeException {

    public S3ServerException() {
        super();
    }

    public S3ServerException(String message) {
        super(message);
    }

    public S3ServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
