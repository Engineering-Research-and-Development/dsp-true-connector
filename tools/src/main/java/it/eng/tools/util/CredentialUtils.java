package it.eng.tools.util;

import org.springframework.stereotype.Component;

@Component
public class CredentialUtils {
	
	private String EDC_AUTH = "{\"region\":\"eu\",\"audience\":\"\r\n"
			+ "http://localhost:8090\",\"clientId\":\"consumer\"\r\n"
			+ "}";

	public String getConnectorCredentials() {
		// TODO replace with Daps JWT
		return  okhttp3.Credentials.basic("connector@mail.com", "password");
	}
	
	public String getAPICredentials() {
		// get from users or from property file instead hardcoded
		 return okhttp3.Credentials.basic("admin@mail.com", "password");
	}
}
