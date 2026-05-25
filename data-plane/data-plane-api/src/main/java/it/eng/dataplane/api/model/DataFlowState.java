package it.eng.dataplane.api.model;

/**
 * Canonical state set for the Data Plane data flow lifecycle, aligned with the
 * Dataspace Protocol signalling specification.
 *
 * <p>Allowed transitions are enforced by {@code DataFlowStateMachine} in {@code data-plane-core}.</p>
 */
public enum DataFlowState {
    /** Flow has been accepted but no resources allocated yet. */
    INITIALIZED,
    /** Resources are being allocated (e.g. pre-signed URL generation). */
    PREPARING,
    /** Resources are allocated; ready to start the transfer. */
    PREPARED,
    /** Transfer initiation is in progress. */
    STARTING,
    /** Transfer is actively running. */
    STARTED,
    /** Transfer has been paused and can be resumed. */
    SUSPENDED,
    /** Transfer finished successfully. */
    COMPLETED,
    /** Transfer was stopped due to an error or explicit termination request. */
    TERMINATED
}
