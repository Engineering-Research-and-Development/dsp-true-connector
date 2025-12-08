package it.eng.datatransfer.exceptions;

import it.eng.datatransfer.model.TransferError;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

@Setter
@Getter
public class DataTransferAPIException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4215112143496569972L;
    private TransferError transferError;

    public DataTransferAPIException(TransferError transferError, String message) {
        super(message);
        this.transferError = transferError;
    }

    public DataTransferAPIException(String message) {
        super(message);
    }

    public DataTransferAPIException(String message, Throwable cause) {
        super(message, cause);
    }

}
