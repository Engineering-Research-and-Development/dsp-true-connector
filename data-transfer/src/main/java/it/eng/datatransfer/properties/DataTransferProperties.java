package it.eng.datatransfer.properties;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class DataTransferProperties {

	@Value("${application.callback.address}")
	private String callbackAddress;

    /**
     *  Returns whether automatic data transfer is enabled.
     */
    @Getter
    @Value("${application.automatic.transfer:false}")
	private boolean automaticTransfer;

    /**
     *  Returns the maximum number of retry attempts for automatic transfer transitions.
     */
    @Getter
    @Value("${application.automatic.transfer.retry.max:3}")
	private int maxRetryAttempts;

    /**
     *  Returns the delay in milliseconds between automatic transfer retry attempts.
     */
    @Getter
    @Value("${application.automatic.transfer.retry.delay.ms:2000}")
	private long retryDelayMs;

	public String providerCallbackAddress() {
		return callbackAddress;
	}

	public String consumerCallbackAddress() {
		String validatedCallback = callbackAddress.endsWith("/") ? callbackAddress.substring(0, callbackAddress.length() - 1) : callbackAddress;
		return validatedCallback + "/consumer";
	}

}
