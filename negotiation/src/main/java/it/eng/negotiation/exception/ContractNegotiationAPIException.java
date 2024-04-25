package it.eng.negotiation.exception;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ContractNegotiationAPIException extends RuntimeException {

	private static final long serialVersionUID = -5195939797427111519L;

    public ContractNegotiationAPIException(String message) {
        super(message);
    }

    public ContractNegotiationAPIException(String message, Throwable cause) {
        super(message, cause);
    }

}
