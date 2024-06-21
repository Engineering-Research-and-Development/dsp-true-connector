package it.eng.tools.exception;


public class ApplicationPropertyNotFoundAPIException extends RuntimeException {

	private static final long serialVersionUID = 9134055136829951530L;

	public ApplicationPropertyNotFoundAPIException() {
		super();
	}

	public ApplicationPropertyNotFoundAPIException(String message) {
		super(message);
	}

}
