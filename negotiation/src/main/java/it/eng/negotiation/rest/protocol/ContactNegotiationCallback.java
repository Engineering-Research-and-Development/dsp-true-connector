package it.eng.negotiation.rest.protocol;

public class ContactNegotiationCallback {

	private static final String OFFERS = "negotiations/offers";
	private static final String CONSUMER_OFFERS = ":callback:/negotiations/:consumerPid:/offers";
	private static final String CONSUMER_AGREEMENT = ":callback:/negotiations/:consumerPid:/agreement";
	private static final String CONSUMER_EVENTS = ":callback:/negotiations/:consumerPid:/events";
	private static final String CONSUMER_TERMINATION = ":callback:/negotiations/:consumerPid:/termination";
	
	/*
	 * Provider 
	 */
	private static final String PROVIDER_NEGOTIATION_OFFER = "callback:/negotiations/offers/cb";
	private static final String PROVIDER_NEGOTIATION_OFFER_CONSUMER = "callback:/negotiations/offers/cb";
	// /{providerPid}/agreement/verification
	private static final String PROVIDER_HANDLE_AGREEMENT = "negotiations/:providerPid:/agreement/verification";
	
	public static String getOffersCallback() {
		return OFFERS;
	}

	public static String getConsumerOffersCallback(String callback, String consumerPid) {
		return CONSUMER_OFFERS.replace(":callback:", callback).replace(":consumerPid:", consumerPid);
	}
	
	public static String getContractAgreementCallback(String callback, String consumerPid) {
		return CONSUMER_AGREEMENT.replace(":callback:", callback).replace(":consumerPid:", consumerPid);
	}
	
	public static String getContractEventsCallback(String callback, String consumerPid) {
		return CONSUMER_EVENTS.replace(":callback:", callback).replace(":consumerPid:", consumerPid);
	}
	
	public static String getContractTerminationCallback(String callback, String consumerPid) {
		return CONSUMER_TERMINATION.replace(":callback:", callback).replace(":consumerPid:", consumerPid);
	}

	/*
	 * Provider
	 */
	public static String getProviderNegotiationOfferCallback(String callback) {
		return PROVIDER_NEGOTIATION_OFFER.replace(":callback:", callback);
	}
	
	public static String getNegotiationOfferConsumer(String callback) {
		return PROVIDER_NEGOTIATION_OFFER_CONSUMER.replace(":callback:", callback);
	}
	
	public static String getProviderHandleAgreementCallback(String providerPid) {
		return PROVIDER_HANDLE_AGREEMENT.replace(":providerPid:", providerPid);
	}
}
