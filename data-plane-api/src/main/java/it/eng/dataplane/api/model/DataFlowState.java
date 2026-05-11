package it.eng.dataplane.api.model;

/** States of a Data Plane data flow lifecycle. */
public enum DataFlowState {
    INITIALIZED, PREPARING, PREPARED, STARTING, STARTED, SUSPENDED, COMPLETED, TERMINATED
}
