package it.eng.tools.exception;

public class BadRequestException extends RuntimeException {

	private static final long serialVersionUID = 4864386447213038584L;
	
	public BadRequestException() {
		super();
	}
	
	public BadRequestException(String message) {
		super(message);
	}

}
