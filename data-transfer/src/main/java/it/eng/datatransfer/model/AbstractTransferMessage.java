package it.eng.datatransfer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.constraints.NotNull;

public abstract class AbstractTransferMessage {
	
	@JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
	private String context = DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE;

	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
	protected String consumerPid;
	
	@JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
	public abstract String getType();

	public String getConsumerPid() {
		return consumerPid;
	}
}
