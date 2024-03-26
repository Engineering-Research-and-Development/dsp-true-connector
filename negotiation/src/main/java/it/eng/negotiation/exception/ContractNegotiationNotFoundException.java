package it.eng.negotiation.exception;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ContractNegotiationNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 4222928711600345366L;
    private String providerPid;

    public ContractNegotiationNotFoundException(String message) {
        super(message);
    }

    public ContractNegotiationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ContractNegotiationNotFoundException(String message, String providerPid) {
        super(message);
        this.providerPid = providerPid;

    }

}
