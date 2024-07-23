package it.eng.tools.exception;

public class ApplicationPropertyErrorException extends RuntimeException {

	private static final long serialVersionUID = 2654979129037852687L;

	public ApplicationPropertyErrorException() {
		super();
	}

	public ApplicationPropertyErrorException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public ApplicationPropertyErrorException(String message, Throwable cause) {
		super(message, cause);
	}

	public ApplicationPropertyErrorException(String message) {
		super(message);
	}

	public ApplicationPropertyErrorException(Throwable cause) {
		super(cause);
	}

	
	
}
