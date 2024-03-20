package it.eng.tools.daps;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DapsCache {
	
	public static final String DUMMY_TOKEN_VALUE = "DummyTokenValue";
	
	private DapsService dapsService;
	private DapsProperties dapsProperties;
	
	private String cachedToken;
	private LocalDateTime expirationTime;
	
	public DapsCache(DapsService dapsService, DapsProperties dapsProperties) {
		this.dapsService = dapsService;
		this.dapsProperties = dapsProperties;
	}
	
	public String getToken() {
		log.info("Requesting token");
		if(!dapsProperties.isEnabledDapsInteraction()) {
			log.info("Daps not configured - continuing with dummy token");
			return DUMMY_TOKEN_VALUE;
		}
		if (dapsProperties.isTokenCaching()) {
			//Checking if cached token is still valid
			synchronized (cachedToken) {
				if (cachedToken == null || LocalDateTime.now().isAfter(expirationTime)) {
					log.info("Fetching new token");
					cachedToken = dapsService.fetchToken();
					if (cachedToken != null) {
						try {
							expirationTime = JWT.decode(cachedToken).getExpiresAt()
									.toInstant()
									.atZone(ZoneId.systemDefault())
								    .toLocalDateTime();
						} catch (JWTDecodeException e) {
							log.error("Could not get token expiration time {}", e.getMessage());
							//Setting to default values since the JWT token was not correct
							cachedToken = null;
							expirationTime = null;
						} 
					} 
				}
			}
			return cachedToken;
		} else {
			//Always new token
			return dapsService.fetchToken();
		}
	}

	public boolean validateToken(String token) {
		return dapsService.validateToken(token);
	}
}
