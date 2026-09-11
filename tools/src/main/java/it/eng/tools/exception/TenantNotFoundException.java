package it.eng.tools.exception;

/**
 * Thrown when a requested tenant cannot be found.
 */
public class TenantNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@link TenantNotFoundException} with the given message.
     *
     * @param message the detail message
     */
    public TenantNotFoundException(String message) {
        super(message);
    }
}
