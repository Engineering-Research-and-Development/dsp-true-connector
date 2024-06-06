package it.eng.negotiation.exception;

import lombok.Getter;

@Getter
public class OfferNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 4222928711600345366L;
	private String consumerPid;
	private String providerPid;

	public OfferNotFoundException(String message) {
		super(message);
	}

	public OfferNotFoundException(String message, String consumerPid, String providerPid) {
		super(message);
		this.consumerPid = consumerPid;
		this.providerPid = providerPid;
	}
}
