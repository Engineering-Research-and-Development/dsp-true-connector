package it.eng.dataplane.api.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.api.DataPlaneConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** DSP DataFlowStatusMessage — sent by Data Plane to Control Plane to report transfer status. */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = DataFlowStatusMessage.Builder.class)
public class DataFlowStatusMessage {

    @NotNull
    @JsonProperty(value = DataPlaneConstants.CONTEXT, access = Access.READ_ONLY)
    private List<String> context = List.of(DataPlaneConstants.DSPACE_2025_01_CONTEXT);

    private String dataFlowId;

    @NotBlank
    private String processId;

    @NotNull
    private DataFlowState state;

    private Map<String, String> dataAddress;
    private String errorMessage;
    private Boolean resumable;

    /** Builder for {@link DataFlowStatusMessage}. */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final DataFlowStatusMessage message = new DataFlowStatusMessage();
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
         * @param dataFlowId data flow identifier
         * @return this builder
         */
        public Builder dataFlowId(String dataFlowId) { message.dataFlowId = dataFlowId; return this; }

        /**
         * Sets the process ID.
         *
         * @param processId transfer process identifier
         * @return this builder
         */
        public Builder processId(String processId) { message.processId = processId; return this; }

        /**
         * Sets the data flow state.
         *
         * @param state current state of the data flow
         * @return this builder
         */
        public Builder state(DataFlowState state) { message.state = state; return this; }

        /**
         * Sets the data address (e.g. presigned URL for PULL transfers).
         *
         * @param dataAddress map of data address properties
         * @return this builder
         */
        public Builder dataAddress(Map<String, String> dataAddress) { message.dataAddress = dataAddress; return this; }

        /**
         * Sets the error message for failed transfers.
         *
         * @param errorMessage error description
         * @return this builder
         */
        public Builder errorMessage(String errorMessage) { message.errorMessage = errorMessage; return this; }

        /**
         * Sets whether the suspended transfer can be resumed.
         *
         * @param resumable true if a valid checkpoint exists and the transfer can be resumed
         * @return this builder
         */
        public Builder resumable(Boolean resumable) { message.resumable = resumable; return this; }

        /**
         * Builds and validates the message.
         *
         * @return validated DataFlowStatusMessage
         * @throws ValidationException if required fields are missing
         */
        public DataFlowStatusMessage build() {
            Set<ConstraintViolation<DataFlowStatusMessage>> violations;
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                violations = factory.getValidator().validate(message);
            }
            if (violations.isEmpty()) return message;
            throw new ValidationException("DataFlowStatusMessage - " +
                violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(", ")));
        }
    }

    /**
     * Returns the DSP message type.
     *
     * @return simple class name
     */
    @JsonProperty(value = DataPlaneConstants.TYPE, access = Access.READ_ONLY)
    public String getType() { return DataFlowStatusMessage.class.getSimpleName(); }
}
