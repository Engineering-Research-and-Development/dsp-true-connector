package it.eng.negotiation.rest.protocol;

public class ContractNegotiationCallback {

	private static final String NEGOTIATION_REQUEST = "/negotiations/request";
	private static final String OFFERS = "/negotiations/offers";
	private static final String CONSUMER_OFFERS = ":callback:/negotiations/:consumerPid:/offers";
	private static final String CONSUMER_AGREEMENT = ":callback:/negotiations/:consumerPid:/agreement";
	private static final String CONSUMER_EVENTS = ":callback:/negotiations/:consumerPid:/events";
	private static final String PROVIDER_TERMINATION = "/negotiations/:providerPid:/termination";
	private static final String CONSUMER_TERMINATION = ":callback:/negotiations/:consumerPid:/termination";
	
	/*
	 * Provider 
	 */
	// /{providerPid}/agreement/verification
	private static final String PROVIDER_AGREEMENT_VERIFICATION = ":callback:/negotiations/:providerPid:/agreement/verification";
	
	public static String getOffersCallback() {
		return OFFERS;
	}

	public static String getNegotiationRequestURL(String protocolAddress) {
		return getValidCallback(protocolAddress) + NEGOTIATION_REQUEST;
	}
 	public static String getConsumerOffersCallback(String callback, String consumerPid) {
		return CONSUMER_OFFERS.replace(":callback:", getValidCallback(callback)).replace(":consumerPid:", consumerPid);
	}
	
	public static String getContractAgreementCallback(String callback, String consumerPid) {
		return CONSUMER_AGREEMENT.replace(":callback:", getValidCallback(callback)).replace(":consumerPid:", consumerPid);
	}
	
	public static String getContractEventsCallback(String callback, String consumerPid) {
		return CONSUMER_EVENTS.replace(":callback:", getValidCallback(callback)).replace(":consumerPid:", consumerPid);
	}
	
	public static String getContractTerminationCallback(String callback, String consumerPid) {
		return CONSUMER_TERMINATION.replace(":callback:", getValidCallback(callback)).replace(":consumerPid:", consumerPid);
	}

	/*
	 * Provider
	 */
	public static String getContractTerminationProvider(String protocolAddress, String providerPid) {
		return getValidCallback(protocolAddress) + PROVIDER_TERMINATION.replace(":providerPid:", providerPid);
	}
	
	public static String getProviderAgreementVerificationCallback(String callback, String providerPid) {
		return PROVIDER_AGREEMENT_VERIFICATION.replace(":callback:", getValidCallback(callback)).replace(":providerPid:", providerPid);
	}
	
	private static String getValidCallback(String callback) {
		return callback.endsWith("/") ? callback.substring(0, callback.length() - 1) : callback;
	}
}
