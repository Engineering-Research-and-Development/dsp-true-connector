package it.eng.datatransfer.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DataTransferProperties {

	@Value("${server.port}")
	private String serverPort;
	
	@Value("${application.callback.address}")
	private String callbackAddress;
	
	public String serverPort() {
		return serverPort;
	}
	
	public String providerCallbackAddress() {
		return callbackAddress;
	}
	
	public String consumerCallbackAddress() {
		String validatedCallback = callbackAddress.endsWith("/") ? callbackAddress.substring(0, callbackAddress.length() - 1) : callbackAddress;
		return validatedCallback + "/consumer";
	}
}
