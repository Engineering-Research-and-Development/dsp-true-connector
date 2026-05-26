package it.eng.dataplane.grpc.model;

/**
 * Lifecycle states of a gRPC stream session managed by this Data Plane.
 */
public enum GrpcSessionState {

    /** Session has been allocated but streaming has not yet started. */
    PREPARED,

    /** Data streaming is in progress. */
    STREAMING,

    /** Session has been terminated and all resources are released. */
    TERMINATED
}
