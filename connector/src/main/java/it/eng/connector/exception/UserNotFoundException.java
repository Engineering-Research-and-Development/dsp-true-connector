package it.eng.connector.exception;

/**
 * Thrown when a requested user cannot be found.
 */
public class UserNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@link it.eng.connector.exception.UserNotFoundException} with the given message.
     *
     * @param message the detail message
     */
    public UserNotFoundException(String message) {
        super(message);
    }
}
