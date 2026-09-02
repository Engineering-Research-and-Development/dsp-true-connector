package it.eng.dataplane.core.model;

import it.eng.dataplane.api.model.DataFlowState;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * MongoDB document representing a data flow managed by this Data Plane instance.
 * Immutable — use {@link Builder#newInstance()} to construct and {@code withXxx()} methods
 * to produce updated copies for state transitions.
 */
@Getter
@Document("data_flows")
public class DataFlowEntity {

    @Id
    private String id;

    @Indexed(unique = true)
    private String processId;

    private String agreementId;
    private String datasetId;
    private String transferType;
    private String callbackAddress;
    private DataFlowState state;
    private Map<String, String> dataAddress;
    private Map<String, Object> metadata;
    private String tenantId;
    private String participantId;
    private String counterPartyId;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    private DataFlowEntity() {
    }

    /**
     * Returns a new {@link DataFlowEntity} with the state and updatedAt timestamp replaced.
     *
     * @param newState the new state
     * @return updated copy
     */
    public DataFlowEntity withState(DataFlowState newState) {
        DataFlowEntity copy = copyOf(this);
        copy.state = newState;
        copy.updatedAt = Instant.now();
        return copy;
    }

    /**
     * Returns a new {@link DataFlowEntity} with the errorMessage and state replaced.
     *
     * @param newErrorMessage the error message
     * @param newState        the new state (typically TERMINATED)
     * @return updated copy
     */
    public DataFlowEntity withError(String newErrorMessage, DataFlowState newState) {
        DataFlowEntity copy = copyOf(this);
        copy.errorMessage = newErrorMessage;
        copy.state = newState;
        copy.updatedAt = Instant.now();
        return copy;
    }

    private static DataFlowEntity copyOf(DataFlowEntity source) {
        DataFlowEntity copy = new DataFlowEntity();
        copy.id = source.id;
        copy.processId = source.processId;
        copy.agreementId = source.agreementId;
        copy.datasetId = source.datasetId;
        copy.transferType = source.transferType;
        copy.callbackAddress = source.callbackAddress;
        copy.state = source.state;
        copy.dataAddress = source.dataAddress;
        copy.metadata = source.metadata;
        copy.tenantId = source.tenantId;
        copy.participantId = source.participantId;
        copy.counterPartyId = source.counterPartyId;
        copy.errorMessage = source.errorMessage;
        copy.createdAt = source.createdAt;
        copy.updatedAt = source.updatedAt;
        return copy;
    }

    /**
     * Builder for {@link DataFlowEntity}.
     */
    public static class Builder {

        private final DataFlowEntity instance = new DataFlowEntity();

        private Builder() {
        }

        /**
         * Creates a new {@link Builder} instance.
         *
         * @return new builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * @param id the MongoDB document id
         * @return this builder
         */
        public Builder id(String id) {
            instance.id = id;
            return this;
        }

        /**
         * @param processId the DSP transfer process id
         * @return this builder
         */
        public Builder processId(String processId) {
            instance.processId = processId;
            return this;
        }

        /**
         * @param agreementId the contract agreement id
         * @return this builder
         */
        public Builder agreementId(String agreementId) {
            instance.agreementId = agreementId;
            return this;
        }

        /**
         * @param datasetId the dataset id (used as S3 object key on provider)
         * @return this builder
         */
        public Builder datasetId(String datasetId) {
            instance.datasetId = datasetId;
            return this;
        }

        /**
         * @param transferType the transfer type (e.g. {@code HttpData-PULL})
         * @return this builder
         */
        public Builder transferType(String transferType) {
            instance.transferType = transferType;
            return this;
        }

        /**
         * @param callbackAddress the CP callback URL for status updates
         * @return this builder
         */
        public Builder callbackAddress(String callbackAddress) {
            instance.callbackAddress = callbackAddress;
            return this;
        }

        /**
         * @param state initial state
         * @return this builder
         */
        public Builder state(DataFlowState state) {
            instance.state = state;
            return this;
        }

        /**
         * @param dataAddress key-value map from the DSP data address
         * @return this builder
         */
        public Builder dataAddress(Map<String, String> dataAddress) {
            instance.dataAddress = dataAddress;
            return this;
        }

        /**
         * @param metadata structured runtime metadata grouped by section
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            instance.metadata = metadata;
            return this;
        }

        /**
         * @param tenantId tenant owning this data flow
         * @return this builder
         */
        public Builder tenantId(String tenantId) {
            instance.tenantId = tenantId;
            return this;
        }

        /**
         * @param participantId participant id
         * @return this builder
         */
        public Builder participantId(String participantId) {
            instance.participantId = participantId;
            return this;
        }

        /**
         * @param counterPartyId counter-party participant id
         * @return this builder
         */
        public Builder counterPartyId(String counterPartyId) {
            instance.counterPartyId = counterPartyId;
            return this;
        }

        /**
         * @param createdAt creation timestamp
         * @return this builder
         */
        public Builder createdAt(Instant createdAt) {
            instance.createdAt = createdAt;
            return this;
        }

        /**
         * @param updatedAt last-updated timestamp
         * @return this builder
         */
        public Builder updatedAt(Instant updatedAt) {
            instance.updatedAt = updatedAt;
            return this;
        }

        /**
         * Builds the {@link DataFlowEntity}.
         *
         * @return the constructed entity
         */
        public DataFlowEntity build() {
            return instance;
        }
    }
}
