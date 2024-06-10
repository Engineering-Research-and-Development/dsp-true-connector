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
	
	public String callbackAddress() {
		return callbackAddress;
	}
	
	public String serverPort() {
		return serverPort;
	}
	
}
