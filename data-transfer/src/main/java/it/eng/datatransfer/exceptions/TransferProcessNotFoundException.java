package it.eng.datatransfer.exceptions;

public class TransferProcessNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 9022977858011839648L;

	public TransferProcessNotFoundException() {
		super();
	}
	
	public TransferProcessNotFoundException(String message) {
		super(message);
	}
}
