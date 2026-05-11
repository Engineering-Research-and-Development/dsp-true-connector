package it.eng.dataplane.api.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Represents a data flow being executed by a Data Plane service. */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Document(collection = "data_flows")
public class DataFlow {

    private String dataFlowId;
    @NotBlank(message = "must not be blank")
    private String processId;
    private String agreementId;
    private String datasetId;
    @NotBlank(message = "must not be blank")
    private String transferType;
    private String callbackAddress;
    private DataFlowState state;
    private Map<String, String> dataAddress;
    private String tenantId;
    private String participantId;
    private String counterPartyId;
    private String errorMessage;
    private LocalDateTime createdAt;
    private Instant updatedAt;

    /** Builder for {@link DataFlow}. */
    public static class Builder {
        private final DataFlow instance = new DataFlow();
        private Builder() {}

        /**
         * Creates a new builder instance.
         *
         * @return new builder
         */
        public static Builder newInstance() { return new Builder(); }

        /**
         * Sets the data flow ID.
         *
         * @param id data flow id
         * @return this builder
         */
        public Builder dataFlowId(String id) { instance.dataFlowId = id; return this; }

        /**
         * Sets the process ID.
         *
         * @param id process id
         * @return this builder
         */
        public Builder processId(String id) { instance.processId = id; return this; }

        /**
         * Sets the agreement ID.
         *
         * @param id agreement id
         * @return this builder
         */
        public Builder agreementId(String id) { instance.agreementId = id; return this; }

        /**
         * Sets the dataset ID.
         *
         * @param id dataset id
         * @return this builder
         */
        public Builder datasetId(String id) { instance.datasetId = id; return this; }

        /**
         * Sets the transfer type.
         *
         * @param type transfer type
         * @return this builder
         */
        public Builder transferType(String type) { instance.transferType = type; return this; }

        /**
         * Sets the callback address.
         *
         * @param addr callback address
         * @return this builder
         */
        public Builder callbackAddress(String addr) { instance.callbackAddress = addr; return this; }

        /**
         * Sets the data flow state.
         *
         * @param state data flow state
         * @return this builder
         */
        public Builder state(DataFlowState state) { instance.state = state; return this; }

        /**
         * Sets the data address map.
         *
         * @param addr data address map
         * @return this builder
         */
        public Builder dataAddress(Map<String, String> addr) { instance.dataAddress = addr; return this; }

        /**
         * Sets the tenant ID.
         *
         * @param id tenant id
         * @return this builder
         */
        public Builder tenantId(String id) { instance.tenantId = id; return this; }

        /**
         * Sets the participant ID.
         *
         * @param id participant id
         * @return this builder
         */
        public Builder participantId(String id) { instance.participantId = id; return this; }

        /**
         * Sets the counter party ID.
         *
         * @param id counter party id
         * @return this builder
         */
        public Builder counterPartyId(String id) { instance.counterPartyId = id; return this; }

        /**
         * Sets the error message.
         *
         * @param errorMessage error message
         * @return this builder
         */
        public Builder errorMessage(String errorMessage) { instance.errorMessage = errorMessage; return this; }

        /**
         * Builds the DataFlow, applying defaults and validating required fields.
         *
         * @return validated DataFlow instance
         * @throws ValidationException if required fields are missing
         */
        public DataFlow build() {
            if (instance.dataFlowId == null) {
                instance.dataFlowId = java.util.UUID.randomUUID().toString();
            }
            if (instance.createdAt == null) instance.createdAt = LocalDateTime.now();
            if (instance.state == null) instance.state = DataFlowState.INITIALIZED;
            Set<ConstraintViolation<DataFlow>> violations;
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                violations = factory.getValidator().validate(instance);
            }
            if (violations.isEmpty()) return instance;
            throw new ValidationException("DataFlow - " +
                violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(",")));
        }
    }
}
