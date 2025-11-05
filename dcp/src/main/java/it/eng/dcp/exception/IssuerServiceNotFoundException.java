package it.eng.dcp.exception;

public class IssuerServiceNotFoundException extends RuntimeException {
    public IssuerServiceNotFoundException(String message) {
        super(message);
    }
}

