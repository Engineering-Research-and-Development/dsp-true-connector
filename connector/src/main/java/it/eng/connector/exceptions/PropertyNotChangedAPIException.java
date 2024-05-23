package it.eng.connector.exceptions;

import java.io.Serial;

public class PropertyNotChangedAPIException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	public PropertyNotChangedAPIException() {
		super();
	}

	public PropertyNotChangedAPIException(String message) {
		super(message);
	}

}
