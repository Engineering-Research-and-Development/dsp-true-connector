
package it.eng.catalog.exceptions;

import java.io.Serial;

public class DistributionNotFoundAPIException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DistributionNotFoundAPIException() {
        super();
    }

    public DistributionNotFoundAPIException(String message) {
        super(message);
    }
}