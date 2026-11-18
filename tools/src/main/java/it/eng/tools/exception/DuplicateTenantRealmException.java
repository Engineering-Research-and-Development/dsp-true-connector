package it.eng.tools.exception;

/**
 * Thrown when a tenant realm is already bound to another tenant.
 */
public class DuplicateTenantRealmException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@link DuplicateTenantRealmException} with the given message.
     *
     * @param message the detail message naming the violated rule and the rejected value
     */
    public DuplicateTenantRealmException(String message) {
        super(message);
    }
}
