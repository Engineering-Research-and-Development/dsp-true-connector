package it.eng.tools.exception;

/**
 * Thrown when an operation is attempted on a tenant that is currently disabled.
 */
public class TenantDisabledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@link TenantDisabledException} with the given message.
     *
     * @param message the detail message
     */
    public TenantDisabledException(String message) {
        super(message);
    }
}
