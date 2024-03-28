package it.eng.negotiation.model;

import com.fasterxml.jackson.annotation.JsonValue;

import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.model.DSpaceConstants.ContractNegotiationEvent;

public enum ContractNegotiationEventType {

	ACCEPTED(DSpaceConstants.DSPACE  + ContractNegotiationEvent.ACCEPTED),
	FINALIZED(DSpaceConstants.DSPACE  + ContractNegotiationEvent.FINALIZED);
	
	private final String eventType;

	ContractNegotiationEventType(final String eventType) {
        this.eventType = eventType;
    }

	public static ContractNegotiationEventType fromEventType(String label) {
	    for (ContractNegotiationEventType e : values()) {
	        if (e.eventType.equals(label)) {
	            return e;
	        }
	    }
	    return null;
	}
	
	@Override
	@JsonValue
    public String toString() {
        return eventType;
    }
}
