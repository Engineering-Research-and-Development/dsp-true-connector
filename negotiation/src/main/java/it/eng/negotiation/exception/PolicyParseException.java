package it.eng.negotiation.exception;

/**
 * Exception thrown when a policy cannot be parsed or is missing required credential types.
 */
public class PolicyParseException extends RuntimeException {

    private final String consumerPid;
    private final String providerPid;

    public PolicyParseException(String message, String consumerPid, String providerPid) {
        super(message);
        this.consumerPid = consumerPid;
        this.providerPid = providerPid;
    }

    public PolicyParseException(String message, String consumerPid, String providerPid, Throwable cause) {
        super(message, cause);
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

