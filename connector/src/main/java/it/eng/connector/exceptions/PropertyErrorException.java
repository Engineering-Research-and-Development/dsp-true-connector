package it.eng.connector.exceptions;

public class PropertyErrorException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public PropertyErrorException() {
		super();
	}

	public PropertyErrorException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public PropertyErrorException(String message, Throwable cause) {
		super(message, cause);
	}

	public PropertyErrorException(String message) {
		super(message);
	}

	public PropertyErrorException(Throwable cause) {
		super(cause);
	}

	
	
}
