package it.eng.catalog.exceptions;

import java.io.Serial;

public class CatalogNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CatalogNotFoundException() {
        super();
    }

    public CatalogNotFoundException(String message) {
        super(message);
    }
}
