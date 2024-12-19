package it.eng.datatransfer.rest.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import it.eng.datatransfer.util.DataTransferMockObjectUtil;

public class DataTransferCallbackTest {

	private String URL_WITHOUT_SLASH = "http://server.com:123/context";
	private String URL_WITH_SLASH = URL_WITHOUT_SLASH + "/";

	@Test
	public void getConsumerDataTransferStart() {
		String replacedUrl = DataTransferCallback.getConsumerDataTransferStart(URL_WITHOUT_SLASH, DataTransferMockObjectUtil.CONSUMER_PID);
		String replacedUrlWithSlash = DataTransferCallback.getConsumerDataTransferStart(URL_WITH_SLASH, DataTransferMockObjectUtil.CONSUMER_PID);
		assertEquals(URL_WITH_SLASH + "transfers/" + DataTransferMockObjectUtil.CONSUMER_PID + "/start", replacedUrl);
		assertEquals(replacedUrl, replacedUrlWithSlash);
	}
	
	@Test
	public void getConsumerDataTransferCompletion() {
		String replacedUrl = DataTransferCallback.getConsumerDataTransferCompletion(URL_WITHOUT_SLASH, DataTransferMockObjectUtil.CONSUMER_PID);
		String replacedUrlWithSlash = DataTransferCallback.getConsumerDataTransferCompletion(URL_WITH_SLASH, DataTransferMockObjectUtil.CONSUMER_PID);
		assertEquals(URL_WITH_SLASH + "transfers/" + DataTransferMockObjectUtil.CONSUMER_PID + "/completion", replacedUrl);
		assertEquals(replacedUrl, replacedUrlWithSlash);
	}
	
	@Test
	public void getConsumerDataTransferTermination() {
		String replacedUrl = DataTransferCallback.getConsumerDataTransferTermination(URL_WITHOUT_SLASH, DataTransferMockObjectUtil.CONSUMER_PID);
		String replacedUrlWithSlash = DataTransferCallback.getConsumerDataTransferTermination(URL_WITH_SLASH, DataTransferMockObjectUtil.CONSUMER_PID);
		assertEquals(URL_WITH_SLASH + "transfers/" + DataTransferMockObjectUtil.CONSUMER_PID + "/termination", replacedUrl);
		assertEquals(replacedUrl, replacedUrlWithSlash);
	}
	
	@Test
	public void getConsumerDataTransferSuspension() {
		String replacedUrl = DataTransferCallback.getConsumerDataTransferSuspension(URL_WITHOUT_SLASH, DataTransferMockObjectUtil.CONSUMER_PID);
		String replacedUrlWithSlash = DataTransferCallback.getConsumerDataTransferSuspension(URL_WITH_SLASH, DataTransferMockObjectUtil.CONSUMER_PID);
		assertEquals(URL_WITH_SLASH + "transfers/" + DataTransferMockObjectUtil.CONSUMER_PID + "/suspension", replacedUrl);
		assertEquals(replacedUrl, replacedUrlWithSlash);
	}
	
}
