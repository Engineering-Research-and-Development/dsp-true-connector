package it.eng.catalog.exceptions;

import java.io.Serial;

public class CatalogNotFoundAPIException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CatalogNotFoundAPIException() {
        super();
    }

    public CatalogNotFoundAPIException(String message) {
        super(message);
    }
}
