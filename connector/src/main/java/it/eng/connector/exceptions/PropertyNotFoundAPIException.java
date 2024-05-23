package it.eng.connector.exceptions;

import java.io.Serial;

public class PropertyNotFoundAPIException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public PropertyNotFoundAPIException() {
		super();
	}

	public PropertyNotFoundAPIException(String message) {
		super(message);
	}

}
