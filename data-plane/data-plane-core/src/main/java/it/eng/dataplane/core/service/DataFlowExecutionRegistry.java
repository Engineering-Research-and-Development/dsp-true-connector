package it.eng.dataplane.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory registry of active data flow execution handles.
 *
 * <p>Protocol implementations register a {@link DataFlowExecutionHandle} when a transfer
 * starts and remove it when the transfer completes or is terminated. The registry provides
 * lookup and cancellation support for lifecycle operations like suspend and terminate.</p>
 */
@Slf4j
@Service
public class DataFlowExecutionRegistry {

    private final ConcurrentMap<String, DataFlowExecutionHandle> handles = new ConcurrentHashMap<>();

    /**
     * Registers an execution handle for the given process ID.
     * Replaces any previously registered handle for the same process ID.
     *
     * @param processId the transfer process ID
     * @param handle    the execution handle to register
     */
    public void register(String processId, DataFlowExecutionHandle handle) {
        handles.put(processId, handle);
        log.debug("Registered execution handle for processId={}", processId);
    }

    /**
     * Finds the execution handle for the given process ID.
     *
     * @param processId the transfer process ID
     * @return optional execution handle, empty if no active transfer
     */
    public Optional<DataFlowExecutionHandle> find(String processId) {
        return Optional.ofNullable(handles.get(processId));
    }

    /**
     * Removes the execution handle for the given process ID.
     * No-op if no handle is registered for that ID.
     *
     * @param processId the transfer process ID
     */
    public void remove(String processId) {
        handles.remove(processId);
        log.debug("Removed execution handle for processId={}", processId);
    }
}
