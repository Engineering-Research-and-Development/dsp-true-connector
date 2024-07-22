package it.eng.datatransfer.exceptions;

public class TransferProcessInvalidStateException extends RuntimeException {

	private static final long serialVersionUID = -8254537435588358784L;

	private String consumerPid;
	private String providerPid;
	
	public TransferProcessInvalidStateException() {
		super();
	}

	public TransferProcessInvalidStateException(String message, String consumerPid, String providerPid) {
		super(message);
		this.consumerPid = consumerPid;
		this.providerPid = providerPid;
	}

	public String getConsumerPid() {
		return consumerPid;
	}

	public String getProviderPid() {
		return providerPid;
	}

}
