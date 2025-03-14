package it.eng.tools.property;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;

@Configuration
@Getter
public class ConnectorProperties {

	@Value("${server.port}")
	private String serverPort;
	@Value("${server.ssl.enabled}")
	private boolean sslEnabled;
	
	public String getConnectorURL() {
		String connectorAddress = sslEnabled ? "https://localhost:" : "http://localhost:";
		return connectorAddress + serverPort;
	}
}
