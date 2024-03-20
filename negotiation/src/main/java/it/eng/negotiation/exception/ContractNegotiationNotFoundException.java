package it.eng.negotiation.exception;

public class ContractNegotiationNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 4222928711600345366L;

	public ContractNegotiationNotFoundException(String message) {
		super(message);
	}
}
