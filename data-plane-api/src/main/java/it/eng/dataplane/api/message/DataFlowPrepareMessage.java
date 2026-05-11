package it.eng.dataplane.api.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** DSP DataFlowPrepareMessage — sent by Control Plane to Data Plane to prepare a transfer. */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = DataFlowPrepareMessage.Builder.class)
public class DataFlowPrepareMessage {

    @NotNull
    @JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
    private List<String> context = List.of(DSpaceConstants.DSPACE_2025_01_CONTEXT);

    @NotNull
    private String processId;

    @NotNull
    private String transferType;

    private String messageId;
    private String participantId;
    private String counterPartyId;
    private String dataspaceContext;
    private String agreementId;
    private String datasetId;
    private String callbackAddress;
    private Map<String, String> dataAddress;
    private Map<String, String> claims;

    /** Builder for {@link DataFlowPrepareMessage}. */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final DataFlowPrepareMessage message = new DataFlowPrepareMessage();
        private Builder() {}

        /**
         * Creates a new builder instance.
         *
         * @return new builder
         */
        public static Builder newInstance() { return new Builder(); }

        /**
         * Sets the process ID.
         *
         * @param processId the transfer process ID
         * @return this builder
         */
        public Builder processId(String processId) { message.processId = processId; return this; }

        /**
         * Sets the transfer type.
         *
         * @param transferType e.g. "HttpData-PUSH"
         * @return this builder
         */
        public Builder transferType(String transferType) { message.transferType = transferType; return this; }

        /**
         * Sets the message ID.
         *
         * @param messageId unique message identifier
         * @return this builder
         */
        public Builder messageId(String messageId) { message.messageId = messageId; return this; }

        /**
         * Sets the participant ID.
         *
         * @param participantId participant identifier
         * @return this builder
         */
        public Builder participantId(String participantId) { message.participantId = participantId; return this; }

        /**
         * Sets the counter party ID.
         *
         * @param counterPartyId counter party identifier
         * @return this builder
         */
        public Builder counterPartyId(String counterPartyId) { message.counterPartyId = counterPartyId; return this; }

        /**
         * Sets the dataspace context.
         *
         * @param dataspaceContext dataspace context value
         * @return this builder
         */
        public Builder dataspaceContext(String dataspaceContext) { message.dataspaceContext = dataspaceContext; return this; }

        /**
         * Sets the agreement ID.
         *
         * @param agreementId the contract agreement ID
         * @return this builder
         */
        public Builder agreementId(String agreementId) { message.agreementId = agreementId; return this; }

        /**
         * Sets the dataset ID.
         *
         * @param datasetId the dataset identifier
         * @return this builder
         */
        public Builder datasetId(String datasetId) { message.datasetId = datasetId; return this; }

        /**
         * Sets the callback address for status callbacks.
         *
         * @param callbackAddress URL for Control Plane status callbacks
         * @return this builder
         */
        public Builder callbackAddress(String callbackAddress) { message.callbackAddress = callbackAddress; return this; }

        /**
         * Sets the data address properties.
         *
         * @param dataAddress map of data address properties
         * @return this builder
         */
        public Builder dataAddress(Map<String, String> dataAddress) { message.dataAddress = dataAddress; return this; }

        /**
         * Sets the claims.
         *
         * @param claims map of claim properties
         * @return this builder
         */
        public Builder claims(Map<String, String> claims) { message.claims = claims; return this; }

        /**
         * Builds and validates the message.
         *
         * @return validated DataFlowPrepareMessage
         * @throws ValidationException if required fields are missing
         */
        public DataFlowPrepareMessage build() {
            Set<ConstraintViolation<DataFlowPrepareMessage>> violations;
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                violations = factory.getValidator().validate(message);
            }
            if (violations.isEmpty()) return message;
            throw new ValidationException("DataFlowPrepareMessage - " +
                violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(", ")));
        }
    }

    /**
     * Returns the DSP message type.
     *
     * @return simple class name
     */
    @JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
    public String getType() { return DataFlowPrepareMessage.class.getSimpleName(); }
}
