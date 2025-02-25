package it.eng.tools.property;

import java.util.Arrays;
import java.util.List;

public interface ApplicationPropertyKeys {
	
	public static final String DAPS_PREFIX = "application.daps";
	public static final String ENABLED_DAPS_INTERACTION_KEY = DAPS_PREFIX + ".enabledDapsInteraction";
	public static final String EXTENDED_TOKEN_VALIDATION_KEY = DAPS_PREFIX + ".extendedTokenValidation";
	public static final String TOKEN_CACHING_KEY = DAPS_PREFIX + ".tokenCaching";
	public static final String FETCH_TOKEN_ON_STARTUP_KEY = DAPS_PREFIX + ".fetchTokenOnStartup";
	public static final String DAPS_URL_KEY = DAPS_PREFIX + ".dapsUrl";
	public static final String DAPS_JWKS_URL_KEY = DAPS_PREFIX + ".dapsJWKSUrl";
	public static final String DAPS_KEYSTORE_NAME_KEY = DAPS_PREFIX + ".dapsKeyStoreName";
	public static final String DAPS_KEYSTORE_PASSWORD_KEY = DAPS_PREFIX + ".dapsKeyStorePassword";
	public static final String DAPS_KEYSTORE_ALIAS_NAME_KEY = DAPS_PREFIX + ".dapsKeystoreAliasName";
	
	public static final String PROTOCOL_AUTHENTICATION = "application.protocol.authentication";
	public static final String PROTOCOL_AUTHENTICATION_ENABLED = PROTOCOL_AUTHENTICATION + ".enabled";
	
	static List<String> getAllTypes() {
		return Arrays.asList(ApplicationPropertyKeys.DAPS_PREFIX, ApplicationPropertyKeys.PROTOCOL_AUTHENTICATION);
	}
}
