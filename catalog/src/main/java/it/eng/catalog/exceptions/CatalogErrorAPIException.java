package it.eng.catalog.exceptions;

import java.io.Serial;

/**
 * Translates to Http.400 Bad request.
 */
public class CatalogErrorAPIException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CatalogErrorAPIException() {
        super();
    }

    public CatalogErrorAPIException(String message) {
        super(message);
    }
}
