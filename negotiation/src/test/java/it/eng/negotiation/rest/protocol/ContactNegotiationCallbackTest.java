package it.eng.negotiation.rest.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

public class ContactNegotiationCallbackTest {

	@Test
	public void callbackTest() {
		String consumerPid = UUID.randomUUID().toString();
		String callback = "callbackTest";
		assertEquals("negotiations/offers", ContactNegotiationCallback.getOffersCallback());
		assertEquals("callbackTest/negotiations/" + consumerPid + "/offers", ContactNegotiationCallback.getConsumerOffersCallback(callback, consumerPid));
		assertEquals("callbackTest/negotiations/" + consumerPid + "/agreement", ContactNegotiationCallback.getContractAgreementCallback(callback, consumerPid));
		assertEquals("callbackTest/negotiations/" + consumerPid + "/events", ContactNegotiationCallback.getContractEventsCallback(callback, consumerPid));
		assertEquals("callbackTest/negotiations/" + consumerPid + "/termination", ContactNegotiationCallback.getContractTerminationCallback(callback, consumerPid));
	}
}
