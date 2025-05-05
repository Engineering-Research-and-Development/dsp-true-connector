package it.eng.datatransfer.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.model.DSpaceConstants.DataTransferStates;

public enum TransferState {
	
	INITIALIZED(DSpaceConstants.DSPACE + DataTransferStates.INITIALIZED) {
		@Override
		public List<TransferState> nextState() {
			return Arrays.asList(REQUESTED);
		}
	},
	REQUESTED(DSpaceConstants.DSPACE + DataTransferStates.REQUESTED) {
		@Override
		public List<TransferState> nextState() {
			return Arrays.asList(STARTED, TERMINATED);
		}
	},
	STARTED(DSpaceConstants.DSPACE + DataTransferStates.STARTED) {
		@Override
		public List<TransferState> nextState() {
			return Arrays.asList(SUSPENDED, COMPLETED, TERMINATED);
		}
	},
	TERMINATED(DSpaceConstants.DSPACE + DataTransferStates.TERMINATED) {
		@Override
		public List<TransferState> nextState() {
			return Arrays.asList();
		}
	},
	COMPLETED(DSpaceConstants.DSPACE + DataTransferStates.COMPLETED) {
		@Override
		public List<TransferState> nextState() {
			return Arrays.asList();
		}
	},
	SUSPENDED(DSpaceConstants.DSPACE + DataTransferStates.SUSPENDED) {
		@Override
		public List<TransferState> nextState() {
			return Arrays.asList(STARTED, TERMINATED);
		}
	};
	
	private final String state;
	private static final Map<String,TransferState> ENUM_MAP;
	public abstract List<TransferState> nextState(); 

	TransferState(final String state) {
	        this.state = state;
	    }

	static {
        Map<String,TransferState> map = new ConcurrentHashMap<String, TransferState>();
        for (TransferState instance : TransferState.values()) {
            map.put(instance.toString(), instance);
            map.put(instance.name(), instance);
        }
        ENUM_MAP = Collections.unmodifiableMap(map);
    }
	
	public static TransferState fromTransferStateState(String state) {
		return ENUM_MAP.get(state.toLowerCase());
	}
	
	public boolean canTransitTo(TransferState state) {
		return nextState().contains(state);
	}
	
	@Override
	@JsonValue
    public String toString() {
        return state;
    }
	
	@JsonCreator
	public static TransferState fromString(String string) {
		TransferState transferState = ENUM_MAP.get(string);
		if (transferState == null) {
			throw new IllegalArgumentException(string + " has no corresponding value");
		}
		return transferState;
	}
}
