package it.eng.tools.exception;

/**
 * Thrown when the Keycloak user API is called for a tenant that has no realm or no usable user-management
 * credentials. Mapped to 409 Conflict.
 */
public class UserManagementNotConfiguredException extends RuntimeException {

    private static final long serialVersionUID = 2175003940263041537L;

    /**
     * Creates the exception.
     *
     * @param message the message; must not contain secrets
     */
    public UserManagementNotConfiguredException(String message) {
        super(message);
    }
}
