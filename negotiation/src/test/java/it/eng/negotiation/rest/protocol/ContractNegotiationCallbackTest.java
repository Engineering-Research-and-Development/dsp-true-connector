package it.eng.negotiation.rest.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import it.eng.negotiation.model.MockObjectUtil;

public class ContractNegotiationCallbackTest {
	
	private String URL_WITHOUT_SLASH = "http://server.com:123/context";
	private String URL_WITH_SLASH = URL_WITHOUT_SLASH + "/";
 
	@Test
	public void getOffersCallback() {
		assertEquals("/negotiations/offers", ContractNegotiationCallback.getOffersCallback());
	}
	
	@Test
	public void getConsumerOffersCallback() {
		String replacedUrl = ContractNegotiationCallback.getConsumerOffersCallback(URL_WITHOUT_SLASH, MockObjectUtil.CONSUMER_PID);
		String replacedUrlWithSlash = ContractNegotiationCallback.getConsumerOffersCallback(URL_WITH_SLASH, MockObjectUtil.CONSUMER_PID);
		assertEquals(URL_WITHOUT_SLASH + "/negotiations/" + MockObjectUtil.CONSUMER_PID + "/offers", replacedUrl);
		assertEquals(replacedUrl, replacedUrlWithSlash);
	}
	
	
	@Test
	public void getContractAgreementCallback() {
		String replacedUrl = ContractNegotiationCallback.getContractAgreementCallback(URL_WITHOUT_SLASH, MockObjectUtil.CONSUMER_PID);
		String replacedUrlWithSlash = ContractNegotiationCallback.getContractAgreementCallback(URL_WITH_SLASH, MockObjectUtil.CONSUMER_PID);
		assertEquals(URL_WITHOUT_SLASH + "/negotiations/" + MockObjectUtil.CONSUMER_PID + "/agreement", replacedUrl);
		assertEquals(replacedUrl, replacedUrlWithSlash);
	}
	
	@Test
	public void getContractEventsCallback() {
		String replacedUrl = ContractNegotiationCallback.getContractEventsCallback(URL_WITHOUT_SLASH, MockObjectUtil.CONSUMER_PID);
		String replacedUrlWithSlash = ContractNegotiationCallback.getContractEventsCallback(URL_WITH_SLASH, MockObjectUtil.CONSUMER_PID);
		assertEquals(URL_WITHOUT_SLASH + "/negotiations/" + MockObjectUtil.CONSUMER_PID + "/events", replacedUrl);
		assertEquals(replacedUrl, replacedUrlWithSlash);
	}
	
	@Test
	public void getContractTerminationCallback() {
		String replacedUrl = ContractNegotiationCallback.getContractTerminationCallback(URL_WITHOUT_SLASH, MockObjectUtil.CONSUMER_PID);
		String replacedUrlWithSlash = ContractNegotiationCallback.getContractTerminationCallback(URL_WITH_SLASH, MockObjectUtil.CONSUMER_PID);
		assertEquals(URL_WITHOUT_SLASH + "/negotiations/" + MockObjectUtil.CONSUMER_PID + "/termination", replacedUrl);
		assertEquals(replacedUrl, replacedUrlWithSlash);
	}
	
	
	@Test
	public void getProviderAgreementVerificationCallback() {
		String replacedUrl = ContractNegotiationCallback.getProviderAgreementVerificationCallback(URL_WITHOUT_SLASH, MockObjectUtil.PROVIDER_PID);
		String replacedUrlWithSlash = ContractNegotiationCallback.getProviderAgreementVerificationCallback(URL_WITH_SLASH, MockObjectUtil.PROVIDER_PID);
		assertEquals(URL_WITHOUT_SLASH + "/negotiations/" + MockObjectUtil.PROVIDER_PID + "/agreement/verification", replacedUrl);
		assertEquals(replacedUrl, replacedUrlWithSlash);
	}
}
