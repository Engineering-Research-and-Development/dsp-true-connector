package it.eng.negotiation.exception;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ContractNegotiationExistsException  extends RuntimeException {
    private String providerPid;
    private String consumerPid;

    public ContractNegotiationExistsException(String message) {
        super(message);
    }

    public ContractNegotiationExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    public ContractNegotiationExistsException(String message, String providerPid) {
        super(message);
        this.providerPid = providerPid;

    }

    public ContractNegotiationExistsException(String message, String providerPid, String consumerPid) {
        super(message);
        this.providerPid = providerPid;
        this.consumerPid = consumerPid;

    }
}
