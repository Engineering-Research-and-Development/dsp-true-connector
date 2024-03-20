package it.eng.datatransfer.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TransferState {
	
	REQUESTED("dspace:REQUESTED"),
	STARTED("dspace:STARTED"),
	TERMINATED("dspace:TERMINATED"),
	COMPLETED("dspace:COMPLETED"),
	SUSPENDED("dspace:SUSPENDED");
	
	private final String state;

	TransferState(final String state) {
	        this.state = state;
	    }

	@Override
	@JsonValue
    public String toString() {
        return state;
    }
}
