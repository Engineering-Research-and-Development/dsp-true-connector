package it.eng.catalog.exceptions;

import java.io.Serial;

public class InternalServerErrorAPIException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InternalServerErrorAPIException() {
        super();
    }

    public InternalServerErrorAPIException(String message) {
        super(message);
    }
}
