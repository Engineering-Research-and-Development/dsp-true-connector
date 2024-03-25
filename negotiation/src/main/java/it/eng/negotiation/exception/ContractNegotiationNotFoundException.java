package it.eng.negotiation.exception;

public class ContractNegotiationNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 4222928711600345366L;
    private String providerId;

    public ContractNegotiationNotFoundException(String message) {
        super(message);
    }

    public ContractNegotiationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ContractNegotiationNotFoundException(String message, String providerId) {
        super(message);
        this.providerId = providerId;

    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }
}
