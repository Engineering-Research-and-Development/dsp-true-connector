package it.eng.datatransfer.exceptions;

import java.io.Serial;

public class TransferProcessNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 9022977858011839648L;

    public TransferProcessNotFoundException() {
        super();
    }

    public TransferProcessNotFoundException(String message) {
        super(message);
    }
}
