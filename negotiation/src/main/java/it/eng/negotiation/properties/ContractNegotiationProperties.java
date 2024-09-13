package it.eng.negotiation.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContractNegotiationProperties {
	
	@Value("${application.callback.address}")
	private String callbackAddress;
	
	@Value("${application.automatic.negotiation}")
	private boolean automaticNegotiation;
	
	@Value("${server.port}")
	private String serverPort;
	
//	@Value("${application.connectorid}")
	public String connectorId() {
		return "connectorId";
	}

	public boolean isAutomaticNegotiation() {
		return automaticNegotiation;
	}
	
	public String providerCallbackAddress() {
		return callbackAddress;
	}
	
	public String consumerCallbackAddress() {
		String validatedCallback = callbackAddress.endsWith("/") ? callbackAddress.substring(0, callbackAddress.length() - 1) : callbackAddress;
		return validatedCallback + "/consumer";
	}
	
	public String serverPort() {
		return serverPort;
	}

	public String getAssignee() {
		return "TRUEConnector v2";
	}
	
}
