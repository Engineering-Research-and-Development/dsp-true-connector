package it.eng.negotiation.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.model.DSpaceConstants.ContractNegotiationStates;

public enum ContractNegotiationState {

	REQUESTED(DSpaceConstants.DSPACE + ContractNegotiationStates.REQUESTED) {
		@Override
		public List<ContractNegotiationState> nextState() {
			return Arrays.asList(OFFERED, AGREED, TERMINATED);
		}
	},
	OFFERED(DSpaceConstants.DSPACE + ContractNegotiationStates.OFFERED) {
		@Override
		public List<ContractNegotiationState> nextState() {
			return Arrays.asList(REQUESTED, ACCEPTED, TERMINATED);
		}
	},
	ACCEPTED(DSpaceConstants.DSPACE + ContractNegotiationStates.ACCEPTED) {
		@Override
		public List<ContractNegotiationState> nextState() {
			return Arrays.asList(AGREED, TERMINATED);
		}
	},
	AGREED(DSpaceConstants.DSPACE + ContractNegotiationStates.AGREED) {
		@Override
		public List<ContractNegotiationState> nextState() {
			return Arrays.asList(VERIFIED, TERMINATED);
		}
	},
	VERIFIED(DSpaceConstants.DSPACE + ContractNegotiationStates.VERIFIED) {
		@Override
		public List<ContractNegotiationState> nextState() {
			return Arrays.asList(FINALIZED, TERMINATED);
		}
	},
	FINALIZED(DSpaceConstants.DSPACE + ContractNegotiationStates.FINALIZED) {
		@Override
		public List<ContractNegotiationState> nextState() {
			return Arrays.asList();
		}
	},
	TERMINATED(DSpaceConstants.DSPACE + ContractNegotiationStates.TERMINATED) {
		@Override
		public List<ContractNegotiationState> nextState() {
			return Arrays.asList();
		}
	};
	
	private final String state;
	private static final Map<String,ContractNegotiationState> ENUM_MAP;
	public abstract List<ContractNegotiationState> nextState(); 
	
	static {
        Map<String,ContractNegotiationState> map = new ConcurrentHashMap<String, ContractNegotiationState>();
        for (ContractNegotiationState instance : ContractNegotiationState.values()) {
            map.put(instance.toString(), instance);//.toLowerCase()
            map.put(instance.name(), instance);
        }
        ENUM_MAP = Collections.unmodifiableMap(map);
    }
	
	@JsonCreator
	public static ContractNegotiationState fromContractNegotiationState(String stateString) {
//		return ENUM_MAP.get(state); //.toLowerCase()
		ContractNegotiationState state = ENUM_MAP.get(stateString);
		if (state == null) {
			throw new IllegalArgumentException(stateString + " has no corresponding value");
		}
		return state;
	}

	ContractNegotiationState(final String state) {
		this.state = state;
	}
	
	public boolean canTransitTo(ContractNegotiationState state) {
		return nextState().contains(state);
	}

	@Override
	@JsonValue
    public String toString() {
        return state;
    }
}
