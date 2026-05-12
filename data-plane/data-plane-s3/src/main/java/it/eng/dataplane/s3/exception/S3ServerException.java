package it.eng.dataplane.s3.exception;

/**
 * Exception thrown when there is an issue with the S3 related operations.
 */
public class S3ServerException extends RuntimeException {

    /**
     * Constructs a new exception with no detail message.
     */
    public S3ServerException() {
        super();
    }

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public S3ServerException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public S3ServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
