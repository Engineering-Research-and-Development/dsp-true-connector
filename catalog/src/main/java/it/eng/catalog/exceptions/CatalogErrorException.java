package it.eng.catalog.exceptions;

public class CatalogErrorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CatalogErrorException() {
        super();
    }

    public CatalogErrorException(String message) {
        super(message);
    }
}
