package it.eng.datatransfer.exceptions;

public class AgreementNotFoundException extends RuntimeException {

	private static final long serialVersionUID = -7682192238680210533L;

	private String consumerPid;
	private String providerPid;
	
	public AgreementNotFoundException() {
		super();
	}
	
	public AgreementNotFoundException(String message, String consumerPid, String providerPid) {
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
