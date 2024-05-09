package it.eng.catalog.exceptions;

import java.io.Serial;

public class DatasetNotFoundAPIException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public DatasetNotFoundAPIException() {
        super();
    }

    public DatasetNotFoundAPIException(String message) {
        super(message);
    }
}

