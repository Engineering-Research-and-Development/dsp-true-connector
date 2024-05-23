package it.eng.catalog.exceptions;

import java.io.Serial;

public class DataServiceNotFoundAPIException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DataServiceNotFoundAPIException() {
        super();
    }

    public DataServiceNotFoundAPIException(String message) {
        super(message);
    }
}

