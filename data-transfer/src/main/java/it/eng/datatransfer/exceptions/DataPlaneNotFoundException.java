package it.eng.datatransfer.exceptions;

import java.io.Serial;

/**
 * Thrown when a Data Plane registration is not found by the given identifier.
 */
public class DataPlaneNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -3142834019918162450L;

    /**
     * Constructs a new exception with the given message.
     *
     * @param message the detail message
     */
    public DataPlaneNotFoundException(String message) {
        super(message);
    }
}
