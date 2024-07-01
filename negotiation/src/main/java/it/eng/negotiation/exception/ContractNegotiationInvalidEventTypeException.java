package it.eng.negotiation.exception;

import lombok.Getter;

@Getter
public class ContractNegotiationInvalidEventTypeException extends RuntimeException {
	
	private static final long serialVersionUID = -8057772669490223184L;
	private String consumerPid;
    private String providerPid;
    
    public ContractNegotiationInvalidEventTypeException(String message, String consumerPid, String providerPid) {
        super(message);
        this.consumerPid = consumerPid;
        this.providerPid = providerPid;
    }

}
