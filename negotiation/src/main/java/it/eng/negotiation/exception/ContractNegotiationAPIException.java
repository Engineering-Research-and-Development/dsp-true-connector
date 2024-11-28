package it.eng.negotiation.exception;

import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ContractNegotiationAPIException extends RuntimeException {

	private static final long serialVersionUID = -5195939797427111519L;
	private ContractNegotiationErrorMessage errorMessage;

	public ContractNegotiationAPIException(ContractNegotiationErrorMessage errorMessage, String message) {
		super(message);
		this.errorMessage = errorMessage;
	}
	
    public ContractNegotiationAPIException(String message) {
        super(message);
    }

    public ContractNegotiationAPIException(String message, Throwable cause) {
        super(message, cause);
    }

}
