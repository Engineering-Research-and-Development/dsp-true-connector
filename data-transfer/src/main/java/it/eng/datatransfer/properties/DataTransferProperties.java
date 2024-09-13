package it.eng.datatransfer.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DataTransferProperties {

	@Value("${server.port}")
	private String serverPort;
	
	public String serverPort() {
		return serverPort;
	}
}
