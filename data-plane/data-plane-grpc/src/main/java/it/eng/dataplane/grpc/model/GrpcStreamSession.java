package it.eng.dataplane.grpc.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Immutable record of a gRPC stream session allocated during the DPS {@code prepare} phase.
 *
 * <p>Each session corresponds to exactly one transfer process. Sessions are stored in
 * {@link it.eng.dataplane.grpc.registry.GrpcSessionRegistry} and released on termination.</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GrpcStreamSession {

    private String sessionId;
    private String processId;
    private String datasetId;
    private boolean finite;
    private String tenantId;
    private GrpcSessionState state;
    private Instant createdAt;

    /**
     * Builder for {@link GrpcStreamSession}.
     */
    public static class Builder {

        private final GrpcStreamSession instance = new GrpcStreamSession();

        private Builder() {
        }

        /**
         * Creates a new builder instance.
         *
         * @return new builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets the unique session identifier.
         *
         * @param sessionId session UUID
         * @return this builder
         */
        public Builder sessionId(String sessionId) {
            instance.sessionId = sessionId;
            return this;
        }

        /**
         * Sets the transfer process ID this session belongs to.
         *
         * @param processId transfer process ID
         * @return this builder
         */
        public Builder processId(String processId) {
            instance.processId = processId;
            return this;
        }

        /**
         * Sets the dataset ID to stream.
         *
         * @param datasetId dataset identifier
         * @return this builder
         */
        public Builder datasetId(String datasetId) {
            instance.datasetId = datasetId;
            return this;
        }

        /**
         * Sets whether this session streams a finite dataset.
         *
         * @param finite {@code true} for finite, {@code false} for non-finite/event streams
         * @return this builder
         */
        public Builder finite(boolean finite) {
            instance.finite = finite;
            return this;
        }

        /**
         * Sets the tenant ID owning this session.
         *
         * @param tenantId tenant identifier
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            instance.tenantId = tenantId;
            return this;
        }

        /**
         * Sets the initial session state.
         *
         * @param state initial lifecycle state
         * @return this builder
         */
        public Builder state(GrpcSessionState state) {
            instance.state = state;
            return this;
        }

        /**
         * Builds the immutable session.
         *
         * @return built session
         */
        public GrpcStreamSession build() {
            if (instance.createdAt == null) {
                instance.createdAt = Instant.now();
            }
            return instance;
        }
    }
}
