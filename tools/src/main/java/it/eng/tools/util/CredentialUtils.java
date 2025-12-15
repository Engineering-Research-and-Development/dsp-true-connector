package it.eng.tools.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Utility class for getting connector credentials.
 *
 * This implementation automatically uses DcpCredentialService if available on classpath,
 * otherwise falls back to basic authentication.
 */
@Component
public class CredentialUtils {

	private static final Logger logger = LoggerFactory.getLogger(CredentialUtils.class);

	private final BeanFactory beanFactory;
	private Object dcpCredentialService;
	private boolean dcpChecked = false;

	@Autowired
	public CredentialUtils(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	/**
	 * Lazy initialization of DcpCredentialService if available.
	 */
	private void initDcpService() {
		if (dcpChecked) {
			return;
		}
		dcpChecked = true;

		try {
			Class<?> dcpServiceClass = Class.forName("it.eng.dcp.service.DcpCredentialService");
			dcpCredentialService = beanFactory.getBean(dcpServiceClass);
			logger.debug("DcpCredentialService found - VP JWT authentication available");
		} catch (ClassNotFoundException e) {
			logger.debug("DcpCredentialService not found - using basic authentication only");
		} catch (Exception e) {
			logger.debug("Could not retrieve DcpCredentialService: {}", e.getMessage());
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
		initDcpService();

		// Try to use DCP service if available
		if (dcpCredentialService != null) {
			try {
				// Call isVpJwtEnabled() via reflection
				Boolean enabled = (Boolean) dcpCredentialService.getClass()
						.getMethod("isVpJwtEnabled")
						.invoke(dcpCredentialService);

				if (enabled != null && enabled) {
					// Call getBearerToken() via reflection
					String bearerToken = (String) dcpCredentialService.getClass()
							.getMethod("getBearerToken")
							.invoke(dcpCredentialService);

					if (bearerToken != null) {
						logger.debug("Using VP JWT for connector authentication");
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
