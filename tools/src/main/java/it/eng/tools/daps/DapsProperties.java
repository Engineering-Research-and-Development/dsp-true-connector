package it.eng.tools.daps;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ConfigurationProperties(prefix = "application.daps")
@Configuration
@Data
@NoArgsConstructor
@Slf4j
public class DapsProperties {

	private boolean enabledDapsInteraction;
	private boolean extendedTokenValidation;
	private boolean tokenCaching;
	private boolean fetchTokenOnStartup;
	private URL dapsUrl;
	private URL dapsJWKSUrl;
	
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
