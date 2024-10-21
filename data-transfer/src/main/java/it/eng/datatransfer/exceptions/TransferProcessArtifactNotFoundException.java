package it.eng.datatransfer.exceptions;

public class TransferProcessArtifactNotFoundException extends RuntimeException {
	private static final long serialVersionUID = -1310522183786288074L;
	
	private String consumerPid;
	private String providerPid;

	public TransferProcessArtifactNotFoundException() {
		super();
	}
	
	public TransferProcessArtifactNotFoundException(String message, String consumerPid, String providerPid) {
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
	
	public TransferProcessArtifactNotFoundException(String message) {
		super(message);
	}
}
