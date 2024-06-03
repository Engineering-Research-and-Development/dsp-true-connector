package it.eng.negotiation.exception;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OfferNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 4222928711600345366L;
	private String providerPid;

	public OfferNotFoundException(String message) {
		super(message);
	}
}
