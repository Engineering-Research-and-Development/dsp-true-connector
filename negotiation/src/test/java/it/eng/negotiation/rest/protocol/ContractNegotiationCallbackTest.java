package it.eng.negotiation.rest.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

public class ContractNegotiationCallbackTest {

	@Test
	public void callbackTest() {
		String consumerPid = UUID.randomUUID().toString();
		String callback = "callbackTest";
		assertEquals("/negotiations/offers", ContractNegotiationCallback.getOffersCallback());
		assertEquals("callbackTest/negotiations/" + consumerPid + "/offers", ContractNegotiationCallback.getConsumerOffersCallback(callback, consumerPid));
		assertEquals("callbackTest/negotiations/" + consumerPid + "/agreement", ContractNegotiationCallback.getContractAgreementCallback(callback, consumerPid));
		assertEquals("callbackTest/negotiations/" + consumerPid + "/events", ContractNegotiationCallback.getContractEventsCallback(callback, consumerPid));
		assertEquals("callbackTest/negotiations/" + consumerPid + "/termination", ContractNegotiationCallback.getContractTerminationCallback(callback, consumerPid));
	}
}
