package it.eng.datatransfer.exceptions;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DataTransferAPIException extends RuntimeException {

	private static final long serialVersionUID = 4215112143496569972L;
	
	public DataTransferAPIException(String message) {
        super(message);
    }

    public DataTransferAPIException(String message, Throwable cause) {
        super(message, cause);
    }

}
