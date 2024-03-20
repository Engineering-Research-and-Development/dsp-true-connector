package it.eng.negotiation.rest.protocol;

public class ContactNegotiationCallback {

	private static final String OFFERS = "negotiations/offers";
	private static final String CONSUMER_OFFERS = ":callback:/negotiations/:consumerPid:/offers";
	private static final String CONSUMER_AGREEMENT = ":callback:/negotiations/:consumerPid:/agreement";
	private static final String CONSUMER_EVENTS = ":callback:/negotiations/:consumerPid:/events";
	private static final String CONSUMER_TERMINATION = ":callback:/negotiations/:consumerPid:/termination";
	
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

}
