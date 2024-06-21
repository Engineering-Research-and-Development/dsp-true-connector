package it.eng.tools.daps;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;

import org.springframework.stereotype.Component;

//import org.springframework.boot.context.properties.ConfigurationProperties;
//import org.springframework.context.annotation.Configuration;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import it.eng.tools.exception.DapsPropertyErrorException;
import it.eng.tools.service.ApplicationPropertiesService;
//import it.eng.tools.service.ApplicationPropertiesService;
//import lombok.Data;
//import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

//@ConfigurationProperties(prefix = "application.daps")
//@Configuration
//@Data
//@NoArgsConstructor
@Component
@Slf4j
public class DapsProperties {

	private final String PREFIX = "application.daps.";
	private final String ENABLED_DAPS_INTERACTION_KEY = PREFIX + "enabledDapsInteraction";
	private final String EXTENDED_TOKEN_VALIDATION_KEY = PREFIX + "extendedTokenValidation";
	private final String TOKEN_CACHING_KEY = PREFIX + "tokenCaching";
	private final String FETCH_TOKEN_ON_STARTUP_KEY = PREFIX + "fetchTokenOnStartup";
	private final String DAPS_URL_KEY = PREFIX + "dapsUrl";
	private final String DAPS_JWKS_URL_KEY = PREFIX + "dapsJWKSUrl";
	private final String DAPS_KEYSTORE_NAME_KEY = PREFIX + "dapsKeyStoreName";
	private final String DAPS_KEYSTORE_PASSWORD_KEY = PREFIX + "dapsKeyStorePassword";
	private final String DAPS_KEYSTORE_ALIAS_NAME_KEY = PREFIX + "dapsKeystoreAliasName";

	/*private boolean enabledDapsInteraction;
	private boolean extendedTokenValidation;
	private boolean tokenCaching;
	private boolean fetchTokenOnStartup;
	private URL dapsUrl;
	private URL dapsJWKSUrl; */

	private final ApplicationPropertiesService service;

	public DapsProperties(ApplicationPropertiesService service) { 
		this.service = service;
	}


	protected boolean isEnabledDapsInteraction() { 
		String enabledDapsInteractionValue = service.get(ENABLED_DAPS_INTERACTION_KEY);

		if(enabledDapsInteractionValue != null) {
			return Boolean.valueOf(enabledDapsInteractionValue);
		}
		return false;
	}

	protected boolean isExtendedTokenValidation() {
		String extendedTokenValidationValue = service.get(EXTENDED_TOKEN_VALIDATION_KEY);

		if(extendedTokenValidationValue != null) {
			return Boolean.valueOf(extendedTokenValidationValue);
		}
		return false;
	}

	protected boolean isTokenCaching() {
		String tokenCachingValue = service.get(TOKEN_CACHING_KEY);

		if(tokenCachingValue != null) {
			return Boolean.valueOf(tokenCachingValue);
		}
		return false;
	}

	protected boolean isFetchTokenOnStartup() {
		String fetchTokenOnStartupValue = service.get(FETCH_TOKEN_ON_STARTUP_KEY);

		if(fetchTokenOnStartupValue != null) {
			return Boolean.valueOf(fetchTokenOnStartupValue);
		}
		return true;
	}

	protected URL getDapsUrl() {
		String dapsUrlValue = service.get(DAPS_URL_KEY);

		URL dapsUrl = null;
		try {
			dapsUrl = new URL(dapsUrlValue);
		} catch (MalformedURLException e) {
			throw new DapsPropertyErrorException(DAPS_URL_KEY + " isn't defined!!!");
		}

		return dapsUrl;
	}

	protected URL getDapsJWKSUrl() {
		String dapsJWKSUrlValue = service.get(DAPS_JWKS_URL_KEY);

		URL dapsJWKSUrl = null;
		try {
			dapsJWKSUrl = new URL(dapsJWKSUrlValue);
		} catch (MalformedURLException e) {
			throw new DapsPropertyErrorException(DAPS_JWKS_URL_KEY + " isn't defined!!!");
		}

		return dapsJWKSUrl;
	}

	protected String getDapsKeyStoreName() {
		String dapsKeyStoreNameValue = service.get(DAPS_KEYSTORE_NAME_KEY);

		if(dapsKeyStoreNameValue == null || dapsKeyStoreNameValue.isEmpty()) {
			throw new DapsPropertyErrorException(DAPS_KEYSTORE_NAME_KEY + " isn't defined!!!");
		}

		return dapsKeyStoreNameValue;
	}

	protected String getDapsKeyStorePassword() {
		String dapsKeyStorePasswordValue = service.get(DAPS_KEYSTORE_PASSWORD_KEY);

		if(dapsKeyStorePasswordValue == null || dapsKeyStorePasswordValue.isEmpty()) {
			throw new DapsPropertyErrorException(DAPS_KEYSTORE_PASSWORD_KEY + " isn't defined!!!");
		}

		return dapsKeyStorePasswordValue;
	}

	protected String getDapsKeystoreAliasName() {
		String dapsKeystoreAliasNameValue = service.get(DAPS_KEYSTORE_ALIAS_NAME_KEY);

		if(dapsKeystoreAliasNameValue == null || dapsKeystoreAliasNameValue.isEmpty()) {
			throw new DapsPropertyErrorException(DAPS_KEYSTORE_ALIAS_NAME_KEY + " isn't defined!!!");
		}

		return dapsKeystoreAliasNameValue;
	}

	public Algorithm getAlogirthm(DecodedJWT jwt) {
		JwkProvider provider = new UrlJwkProvider(getDapsJWKSUrl());
		Jwk jwk;
		Algorithm algorithm = null;
		try {
			jwk = provider.get(jwt.getKeyId());
			algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
		} catch (JwkException e) {
			log.error("Error while trying to validate token {}", e);
		}
		return algorithm;
	}

}
