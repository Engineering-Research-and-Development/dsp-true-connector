package it.eng.dataplane.grpc.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
    /** CP-selected source type (e.g., "s3", "gcs") persisted at prepare time. */
    private String sourceType;
    /** CP-provided source S3 access properties persisted at prepare time. */
    private Map<String, String> sourceProperties;

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
         * Sets the CP-selected source type (e.g., "s3", "gcs") to be used for stream reads.
         *
         * <p>This source type is extracted from the {@code source.sourceType} metadata field
         * in the {@code DataFlowPrepareMessage} so that {@code DataStreamService} can
         * resolve the correct {@link it.eng.dataplane.api.io.SourceReader} without relying
         * on hardcoded defaults.</p>
         *
         * @param sourceType source type identifier (e.g., "s3")
         * @return this builder
         */
        public Builder sourceType(String sourceType) {
            instance.sourceType = sourceType;
            return this;
        }

        /**
         * Sets the CP-provided source S3 access properties to persist for later stream reads.
         *
         * <p>These properties are populated from the {@code source.*} section of the
         * {@code DataFlowPrepareMessage} metadata so that {@code DataStreamService} can
         * build a {@link it.eng.dataplane.api.io.SourceContext} without relying on local
         * {@code s3.bucketName} configuration.</p>
         *
         * @param sourceProperties map keyed by {@code it.eng.tools.s3.util.S3Utils} constants
         * @return this builder
         */
        public Builder sourceProperties(Map<String, String> sourceProperties) {
            instance.sourceProperties = sourceProperties == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(sourceProperties));
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
            if (instance.sourceProperties == null) {
                instance.sourceProperties = Collections.emptyMap();
            }
            return instance;
        }
    }

    /**
     * Returns the CP-provided source S3 access properties for this session.
     *
     * <p>Will be empty when the session was created without explicit source properties.
     * Post-Task-6 all sessions produced by
     * {@link it.eng.dataplane.grpc.GrpcStreamTransferProtocol#prepare} carry these.</p>
     *
     * @return immutable source property map; never {@code null}
     */
    public Map<String, String> getSourceProperties() {
        return sourceProperties == null ? Collections.emptyMap() : sourceProperties;
    }
}
