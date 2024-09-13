package it.eng.negotiation.exception;

import lombok.Getter;

@Getter
public class OfferNotValidException extends RuntimeException {

	private static final long serialVersionUID = 5188495823335252753L;
	private String consumerPid;
	private String providerPid;

	public OfferNotValidException(String message) {
		super(message);
	}

	public OfferNotValidException(String message, String consumerPid, String providerPid) {
		super(message);
		this.consumerPid = consumerPid;
		this.providerPid = providerPid;
	}
}