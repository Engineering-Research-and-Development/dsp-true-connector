package it.eng.dataplane.core.service;

import it.eng.dataplane.api.model.DataFlowState;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * Enforces valid state transitions for the data flow lifecycle.
 *
 * <p>Transition rules follow the canonical Dataspace Protocol signalling specification.
 * Call {@link #assertTransition(DataFlowState, DataFlowState)} before persisting any state change
 * to detect invalid transitions early.</p>
 */
@Service
public class DataFlowStateMachine {

    private static final Map<DataFlowState, Set<DataFlowState>> ALLOWED = Map.of(
            DataFlowState.INITIALIZED, Set.of(
                    DataFlowState.PREPARING, DataFlowState.PREPARED,
                    DataFlowState.STARTING, DataFlowState.STARTED, DataFlowState.TERMINATED),
            DataFlowState.PREPARING, Set.of(DataFlowState.PREPARED, DataFlowState.TERMINATED),
            DataFlowState.PREPARED, Set.of(
                    DataFlowState.STARTING, DataFlowState.STARTED, DataFlowState.TERMINATED),
            DataFlowState.STARTING, Set.of(DataFlowState.STARTED, DataFlowState.TERMINATED),
            DataFlowState.STARTED, Set.of(
                    DataFlowState.SUSPENDED, DataFlowState.COMPLETED, DataFlowState.TERMINATED),
            DataFlowState.SUSPENDED, Set.of(DataFlowState.STARTED, DataFlowState.TERMINATED),
            DataFlowState.COMPLETED, Set.of(),
            DataFlowState.TERMINATED, Set.of());

    /**
     * Verifies that the transition from {@code from} to {@code to} is permitted.
     *
     * @param from current state
     * @param to   target state
     * @throws IllegalStateException if the transition is not in the allowed set
     */
    public void assertTransition(DataFlowState from, DataFlowState to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("Invalid DataFlow transition: " + from + " -> " + to);
        }
    }
}
