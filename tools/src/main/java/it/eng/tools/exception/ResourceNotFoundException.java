package it.eng.tools.exception;

public class ResourceNotFoundException extends RuntimeException {

	private static final long serialVersionUID = -4822197703294666016L;

	public ResourceNotFoundException() {
		super();
	}
	
	public ResourceNotFoundException(String message) {
		super(message);
	}
}
