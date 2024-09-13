package it.eng.negotiation.exception;

import lombok.Getter;

@Getter
public class ProviderPidNotBlankException extends RuntimeException {

	private String consumerPid;

	/**
	 * 
	 */
	private static final long serialVersionUID = -5856773248750551506L;

	public ProviderPidNotBlankException(String message) {
		super(message);
	}

	public ProviderPidNotBlankException(String message, String consumerPid) {
		super(message);
		this.consumerPid = consumerPid;
	}
}
