package it.eng.negotiation.model;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonValue;

import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.model.DSpaceConstants.ContractNegotiationStates;

public enum ContractNegotiationState {

	REQUESTED(DSpaceConstants.DSPACE + ContractNegotiationStates.REQUESTED),
	OFFERED(DSpaceConstants.DSPACE + ContractNegotiationStates.OFFERED),
	ACCEPTED(DSpaceConstants.DSPACE + ContractNegotiationStates.ACCEPTED),
	AGREED(DSpaceConstants.DSPACE + ContractNegotiationStates.AGREED),
	VERIFIED(DSpaceConstants.DSPACE + ContractNegotiationStates.VERIFIED),
	FINALIZED(DSpaceConstants.DSPACE + ContractNegotiationStates.FINALIZED),
	TERMINATED(DSpaceConstants.DSPACE + ContractNegotiationStates.TERMINATED);
	
	private final String state;
	private static final Map<String,ContractNegotiationState> ENUM_MAP;
	
	static {
        Map<String,ContractNegotiationState> map = new ConcurrentHashMap<String, ContractNegotiationState>();
        for (ContractNegotiationState instance : ContractNegotiationState.values()) {
            map.put(instance.toString().toLowerCase(), instance);
        }
        ENUM_MAP = Collections.unmodifiableMap(map);
    }
	
	public static ContractNegotiationState fromContractNegotiationState(String state) {
		return ENUM_MAP.get(state.toLowerCase());
	}

	ContractNegotiationState(final String state) {
		this.state = state;
	}

	@Override
	@JsonValue
    public String toString() {
        return state;
    }
}
