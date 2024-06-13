package it.eng.datatransfer.exceptions;

public class AgreementNotFoundException extends RuntimeException {

	private static final long serialVersionUID = -7682192238680210533L;

	public AgreementNotFoundException() {
		super();
	}
	
	public AgreementNotFoundException(String message) {
		super(message);
	}
}
