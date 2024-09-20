package it.eng.negotiation.model;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonCreator;
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
	
	private static final Map<String,ContractNegotiationEventType> BY_LABEL;
	static {
        Map<String,ContractNegotiationEventType> map = new ConcurrentHashMap<String, ContractNegotiationEventType>();
        for (ContractNegotiationEventType instance : ContractNegotiationEventType.values()) {
            map.put(instance.toString(), instance);
            map.put(instance.name(), instance);
            map.put("https://w3id.org/dspace/v0.8/"	+ instance.name(), instance);
        }
        BY_LABEL = Collections.unmodifiableMap(map);
    }

	public static ContractNegotiationEventType fromEventType(String label) {
	  return BY_LABEL.get(label);
	}
	
	@Override
	@JsonValue
    public String toString() {
        return eventType;
    }
	
	@JsonCreator
	public static ContractNegotiationEventType fromString(String string) {
		ContractNegotiationEventType contractNegotiationEventType = BY_LABEL.get(string);
		if (contractNegotiationEventType == null) {
			throw new IllegalArgumentException(string + " has no corresponding value");
		}
		return contractNegotiationEventType;
	}
}
