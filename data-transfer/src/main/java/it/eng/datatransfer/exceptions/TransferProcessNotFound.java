package it.eng.datatransfer.exceptions;

public class TransferProcessNotFound extends RuntimeException {

	private static final long serialVersionUID = 9022977858011839648L;

	public TransferProcessNotFound() {
		super();
	}
	
	public TransferProcessNotFound(String message) {
		super(message);
	}
}
