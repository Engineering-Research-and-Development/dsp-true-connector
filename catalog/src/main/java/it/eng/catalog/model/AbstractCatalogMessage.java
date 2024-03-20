package it.eng.catalog.model;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import it.eng.tools.model.DSpaceConstants;

public abstract class AbstractCatalogMessage {

	@JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
	private String context = DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE;
	
	/**
	 * Can be optional.
	 * If the message does not include a consumerPid, a new contract negotiation will be created on consumer 
	 * side and the consumer selects an appropriate consumerPid
	 */

	@JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
	public abstract String getType();
	
	protected String createNewId() {
		return "urn:uuid:" + UUID.randomUUID();
	}
	
}
