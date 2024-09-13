package it.eng.catalog.exceptions;

import java.io.Serial;

public class ResourceNotFoundAPIException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundAPIException() {
        super();
    }

    public ResourceNotFoundAPIException(String message) {
        super(message);
    }
}
