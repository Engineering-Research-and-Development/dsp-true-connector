package it.eng.tools.property;

import java.util.Arrays;
import java.util.List;

public interface ApplicationPropertyKeys {
	
	public static final String PROTOCOL_AUTHENTICATION = "application.protocol.authentication";
	public static final String PROTOCOL_AUTHENTICATION_ENABLED = PROTOCOL_AUTHENTICATION + ".enabled";
	
	static List<String> getAllTypes() {
		return Arrays.asList(ApplicationPropertyKeys.PROTOCOL_AUTHENTICATION);
	}
}
