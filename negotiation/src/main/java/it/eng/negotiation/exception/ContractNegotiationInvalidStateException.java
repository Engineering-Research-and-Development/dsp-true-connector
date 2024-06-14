package it.eng.negotiation.exception;

import lombok.Getter;

@Getter
public class ContractNegotiationInvalidStateException extends RuntimeException {
	
	private static final long serialVersionUID = -6532256878967742394L;
	private String consumerPid;
    private String providerPid;
    
    public ContractNegotiationInvalidStateException(String message, String consumerPid, String providerPid) {
        super(message);
        this.consumerPid = consumerPid;
        this.providerPid = providerPid;
    }

}
