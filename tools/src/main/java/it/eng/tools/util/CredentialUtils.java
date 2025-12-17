package it.eng.tools.util;

import it.eng.tools.credential.VpCredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Utility class for getting connector credentials.
 *
 * This implementation automatically uses VpCredentialService if available on classpath,
 * otherwise falls back to basic authentication.
 */
@Component
public class CredentialUtils {

	private static final Logger logger = LoggerFactory.getLogger(CredentialUtils.class);

	private final BeanFactory beanFactory;
	private VpCredentialService vpCredentialService;
	private boolean dcpChecked = false;

	@Autowired
	public CredentialUtils(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	/**
	 * Lazy initialization of VpCredentialService if available.
	 */
	private void initDcpService() {
		if (dcpChecked) {
			return;
		}
		dcpChecked = true;

		try {
			vpCredentialService = beanFactory.getBean(VpCredentialService.class);
			logger.debug("VpCredentialService found - VP JWT authentication available");
		} catch (Exception e) {
			logger.debug("VpCredentialService not found - using basic authentication only: {}", e.getMessage());
		}
	}

	/**
	 * Get connector credentials for protocol communication.
	 *
	 * Automatically uses VP JWT if DcpCredentialService is available and enabled,
	 * otherwise returns basic authentication credentials.
	 *
	 * @return Authorization header value ("Bearer {VP_JWT}" or "Basic {base64}")
	 */
	public String getConnectorCredentials() {
		return getConnectorCredentials(null);
	}

	/**
	 * Get connector credentials for protocol communication to a specific target.
	 *
	 * <p>This method extracts the verifier DID from the target URL and passes it to
	 * the DCP service for dynamic token generation. This allows the connector to
	 * communicate with multiple verifiers without hardcoding the verifier DID in
	 * configuration.
	 *
	 * <p>The target URL is converted to a DID as follows:
	 * <ul>
	 *   <li>https://verifier.com/catalog → did:web:verifier.com</li>
	 *   <li>https://localhost:8080/dsp → did:web:localhost%3A8080</li>
	 * </ul>
	 *
	 * @param targetUrl The full URL where the request will be sent (e.g., "https://verifier.com/catalog/request")
	 * @return Authorization header value ("Bearer {JWT}" or "Basic {base64}")
	 */
	public String getConnectorCredentials(String targetUrl) {
		initDcpService();

		// Try to use VP credential service if available
		if (vpCredentialService != null) {
			try {
				// Check if VP JWT is enabled
				if (vpCredentialService.isVpJwtEnabled()) {
					// If targetUrl is provided, use it for dynamic verifier DID extraction
					String bearerToken = vpCredentialService.getBearerToken(targetUrl);

					if (bearerToken != null) {
						if (targetUrl != null && !targetUrl.isBlank()) {
							logger.debug("Using VP JWT with dynamic verifier DID from URL: {}", targetUrl);
						} else {
							logger.debug("Using VP JWT for connector authentication");
						}
						return bearerToken;
					} else {
						logger.warn("VP JWT generation returned null - falling back to basic auth");
					}
				}
			} catch (Exception e) {
				logger.warn("Failed to get VP JWT - falling back to basic auth: {}", e.getMessage());
			}
		}

		// Fallback to basic authentication
		logger.debug("Using basic authentication for connector credentials");
		return okhttp3.Credentials.basic("connector@mail.com", "password");
	}
	
	public String getAPICredentials() {
		// get from users or from property file instead hardcoded
		 return okhttp3.Credentials.basic("admin@mail.com", "password");
	}
}
