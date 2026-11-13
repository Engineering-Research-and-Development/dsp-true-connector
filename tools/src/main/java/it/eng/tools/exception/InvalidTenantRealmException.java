package it.eng.tools.exception;

/**
 * Thrown when a tenant realm violates the realm naming rules: it does not match the allowed
 * pattern, exceeds the maximum length, or is a reserved realm name.
 */
public class InvalidTenantRealmException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@link InvalidTenantRealmException} with the given message.
     *
     * @param message the detail message naming the violated rule and the rejected value
     */
    public InvalidTenantRealmException(String message) {
        super(message);
    }
}
