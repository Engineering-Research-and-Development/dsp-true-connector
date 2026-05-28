package it.eng.dataplane.core.service;

/**
 * Handle to an active data flow execution, allowing the registry to track and cancel
 * in-progress transfers.
 *
 * <p>Protocol implementations that support cancellation should return an instance of this
 * interface when initiating a transfer. The handle is stored in
 * {@link DataFlowExecutionRegistry} keyed by {@code processId}.</p>
 */
public interface DataFlowExecutionHandle {

    /**
     * Returns the transfer process ID associated with this handle.
     *
     * @return the DSP transfer process ID
     */
    String getProcessId();

    /**
     * Requests cancellation of the in-progress transfer.
     * Best-effort: implementations should attempt to stop work and release resources
     * but must not throw.
     */
    void cancel();
}
