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
	private int maxRetries;

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
	 * Returns the maximum number of <em>retries</em> for automatic negotiation transitions.
	 * The total number of attempts is {@code maxRetries + 1}: one initial attempt plus up to
	 * {@code maxRetries} retries. Setting this to {@code 0} means no retries — a single
	 * failure goes straight to {@code TERMINATED}.
	 *
	 * @return maximum number of retries before transitioning to TERMINATED
	 */
	public int getMaxRetries() {
		return maxRetries;
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
