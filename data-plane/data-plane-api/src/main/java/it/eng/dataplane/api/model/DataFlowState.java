package it.eng.dataplane.api.model;

/** States of a Data Plane data flow lifecycle. */
public enum DataFlowState {
    INITIALIZED, STARTED, TRANSFERRED, COMPLETED, SUSPENDED, TERMINATED, FAILED, REQUESTED
}
