package it.eng.negotiation.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContractNegotiationProperties {
	
	@Value("${application.callback.address}")
	private String callbackAddress;
	
	@Value("${application.automatic.negotiation}")
	private boolean automaticNegotiation;

	@Value("${application.automatic.negotiation.retry.max:3}")
	private int maxRetryAttempts;

	@Value("${application.automatic.negotiation.retry.delay.ms:2000}")
	private long retryDelayMs;

	@Value("${server.port}")
	private String serverPort;
	
//	@Value("${application.connectorid}")
	public String connectorId() {
		return "connectorId";
	}

	public boolean isAutomaticNegotiation() {
		return automaticNegotiation;
	}

	/**
	 * Returns the maximum number of retry attempts for automatic negotiation transitions.
	 *
	 * @return max retry attempts before transitioning to TERMINATED
	 */
	public int getMaxRetryAttempts() {
		return maxRetryAttempts;
	}

	/**
	 * Returns the delay in milliseconds between automatic negotiation retry attempts.
	 *
	 * @return retry delay in milliseconds
	 */
	public long getRetryDelayMs() {
		return retryDelayMs;
	}

	public String providerCallbackAddress() {
		return callbackAddress;
	}
	
	public String consumerCallbackAddress() {
		String validatedCallback = callbackAddress.endsWith("/") ? callbackAddress.substring(0, callbackAddress.length() - 1) : callbackAddress;
		return validatedCallback + "/consumer";
	}
	
	public String serverPort() {
		return serverPort;
	}

	public String getAssignee() {
		return "TRUEConnector v2";
	}
	
}
