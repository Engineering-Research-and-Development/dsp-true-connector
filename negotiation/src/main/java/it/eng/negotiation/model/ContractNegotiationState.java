package it.eng.negotiation.model;

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

	ContractNegotiationState(final String state) {
	        this.state = state;
	    }

	@Override
	@JsonValue
    public String toString() {
        return state;
    }
}
