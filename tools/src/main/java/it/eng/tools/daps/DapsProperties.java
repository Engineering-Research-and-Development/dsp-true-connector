package it.eng.tools.daps;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;

import org.springframework.stereotype.Component;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import it.eng.tools.exception.DapsPropertyErrorException;
import it.eng.tools.property.ApplicationPropertyKeys;
import it.eng.tools.service.ApplicationPropertiesService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DapsProperties {

	private final ApplicationPropertiesService service;

	public DapsProperties(ApplicationPropertiesService service) {
		this.service = service;
	}

	protected boolean isEnabledDapsInteraction() {
		String enabledDapsInteractionValue = service.get(ApplicationPropertyKeys.ENABLED_DAPS_INTERACTION_KEY);

		if(enabledDapsInteractionValue != null) {
			return Boolean.valueOf(enabledDapsInteractionValue);
		}
		return false;
	}

	protected boolean isExtendedTokenValidation() {
		String extendedTokenValidationValue = service.get(ApplicationPropertyKeys.EXTENDED_TOKEN_VALIDATION_KEY);

		if(extendedTokenValidationValue != null) {
			return Boolean.valueOf(extendedTokenValidationValue);
		}
		return false;
	}

	protected boolean isTokenCaching() {
		String tokenCachingValue = service.get(ApplicationPropertyKeys.TOKEN_CACHING_KEY);

		if(tokenCachingValue != null) {
			return Boolean.valueOf(tokenCachingValue);
		}
		return false;
	}

	protected boolean isFetchTokenOnStartup() {
		String fetchTokenOnStartupValue = service.get(ApplicationPropertyKeys.FETCH_TOKEN_ON_STARTUP_KEY);

		if(fetchTokenOnStartupValue != null) {
			return Boolean.valueOf(fetchTokenOnStartupValue);
		}
		return true;
	}

	protected URL getDapsUrl() {
		String dapsUrlValue = service.get(ApplicationPropertyKeys.DAPS_URL_KEY);

		URL dapsUrl = null;
		try {
			dapsUrl = new URL(dapsUrlValue);
		} catch (MalformedURLException e) {
			throw new DapsPropertyErrorException(ApplicationPropertyKeys.DAPS_URL_KEY + " isn't defined!!!");
		}

		return dapsUrl;
	}

	protected URL getDapsJWKSUrl() {
		String dapsJWKSUrlValue = service.get(ApplicationPropertyKeys.DAPS_JWKS_URL_KEY);

		URL dapsJWKSUrl = null;
		try {
			dapsJWKSUrl = new URL(dapsJWKSUrlValue);
		} catch (MalformedURLException e) {
			throw new DapsPropertyErrorException(ApplicationPropertyKeys.DAPS_JWKS_URL_KEY + " isn't defined!!!");
		}

		return dapsJWKSUrl;
	}

	protected String getDapsKeyStoreName() {
		String dapsKeyStoreNameValue = service.get(ApplicationPropertyKeys.DAPS_KEYSTORE_NAME_KEY);

		if(dapsKeyStoreNameValue == null || dapsKeyStoreNameValue.isEmpty()) {
			throw new DapsPropertyErrorException(ApplicationPropertyKeys.DAPS_KEYSTORE_NAME_KEY + " isn't defined!!!");
		}

		return dapsKeyStoreNameValue;
	}

	protected String getDapsKeyStorePassword() {
		String dapsKeyStorePasswordValue = service.get(ApplicationPropertyKeys.DAPS_KEYSTORE_PASSWORD_KEY);

		if(dapsKeyStorePasswordValue == null || dapsKeyStorePasswordValue.isEmpty()) {
			throw new DapsPropertyErrorException(ApplicationPropertyKeys.DAPS_KEYSTORE_PASSWORD_KEY + " isn't defined!!!");
		}

		return dapsKeyStorePasswordValue;
	}

	protected String getDapsKeystoreAliasName() {
		String dapsKeystoreAliasNameValue = service.get(ApplicationPropertyKeys.DAPS_KEYSTORE_ALIAS_NAME_KEY);

		if(dapsKeystoreAliasNameValue == null || dapsKeystoreAliasNameValue.isEmpty()) {
			throw new DapsPropertyErrorException(ApplicationPropertyKeys.DAPS_KEYSTORE_ALIAS_NAME_KEY + " isn't defined!!!");
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
