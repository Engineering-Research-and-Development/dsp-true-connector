package it.eng.tools.util;

import org.springframework.stereotype.Component;

@Component
public class CredentialUtils {

	public String getConnectorCredentials() {
		// TODO replace with Daps JWT
		return  okhttp3.Credentials.basic("connector@mail.com", "password");
//		return "{\"region\":\"eu\",\"audience\":\"http://localhost:8090\",\"clientId\":\"pera\"}";
	}
	
	public String getAPICredentials() {
		// get from users or from property file instead hardcoded
		 return okhttp3.Credentials.basic("admin@mail.com", "password");
	}
}
