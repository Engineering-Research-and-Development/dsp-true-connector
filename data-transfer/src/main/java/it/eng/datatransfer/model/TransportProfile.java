package it.eng.datatransfer.model;

/**
 * Internal transport profile constants for routing transfers to specialized Data Plane instances.
 *
 * <p>Transport profiles are internal identifiers used by the Control Plane routing layer.
 * They are not part of the DSP protocol and must not appear in protocol-facing messages.</p>
 */
public final class TransportProfile {

    /**
     * gRPC streaming transport profile.
     * Data Planes that implement gRPC-based streaming advertise this profile at registration.
     */
    public static final String STREAM_GRPC = "stream:grpc";

    /**
     * Kafka streaming transport profile.
     * Data Planes that implement Kafka-backed streaming advertise this profile at registration.
     */
    public static final String STREAM_KAFKA = "stream:kafka";

    private TransportProfile() {
    }
}
