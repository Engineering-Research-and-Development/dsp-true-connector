package it.eng.negotiation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = ContractRequestMessage.Builder.class)
@JsonPropertyOrder(value = {DSpaceConstants.CONTEXT, DSpaceConstants.TYPE, DSpaceConstants.ID, DSpaceConstants.CONSUMER_PID, DSpaceConstants.PROVIDER_PID, DSpaceConstants.CALLBACK_ADDRESS}, alphabetic = true)
public class ContractRequestMessage {

    @NotNull
    @JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
    private List<String> context = List.of(DSpaceConstants.DSPACE_2025_01_CONTEXT);

    @NotNull
    private String consumerPid;

    //  either providerPid or callbackAddress must be present, but not both!
    private String providerPid;
    private String callbackAddress;

    @NotNull
    private Offer offer;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final ContractRequestMessage message;

        private Builder() {
            message = new ContractRequestMessage();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder consumerPid(String consumerPid) {
            message.consumerPid = consumerPid;
            return this;
        }

        public Builder providerPid(String providerPid) {
            message.providerPid = providerPid;
            return this;
        }

        public Builder callbackAddress(String callbackAddress) {
            message.callbackAddress = callbackAddress;
            return this;
        }

        public Builder offer(Offer offer) {
            message.offer = offer;
            return this;
        }

        public ContractRequestMessage build() {
            Set<ConstraintViolation<ContractRequestMessage>> violations;
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                violations = factory.getValidator().validate(message);
            }

             // Collect existing validator messages
             java.util.List<String> messages = violations
                     .stream()
                     .map(v -> v.getPropertyPath() + " " + v.getMessage())
                     .collect(Collectors.toList());

             // Custom validation: either providerPid or callbackAddress must be present
             if ((message.getProviderPid() == null && message.getCallbackAddress() == null) ||
             (message.getProviderPid() != null && message.getCallbackAddress() != null)) {
                 messages.add("either providerPid or callbackAddress must be present");
             }

             if (messages.isEmpty()) {
                 return message;
             }
             throw new ValidationException("ContractRequestMessage - " + String.join(", ", messages));
        }
    }

    @JsonProperty(value = DSpaceConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return ContractRequestMessage.class.getSimpleName();
    }

}
