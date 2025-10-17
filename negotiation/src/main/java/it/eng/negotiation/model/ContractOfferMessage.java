package it.eng.negotiation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Set;
import java.util.stream.Collectors;

@Getter
@JsonDeserialize(builder = ContractOfferMessage.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ContractOfferMessage extends AbstractNegotiationObject {

    //  either consumerPid or callbackAddress must be present, but not both!
    private String consumerPid;
    private String callbackAddress;

    @NotNull
    private Offer offer;


    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final ContractOfferMessage message;

        private Builder() {
            message = new ContractOfferMessage();
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

        public ContractOfferMessage build() {
            Set<ConstraintViolation<ContractOfferMessage>> violations;
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                violations = factory.getValidator().validate(message);
            }

            // Collect existing validator messages
            java.util.List<String> messages = violations
                    .stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.toList());

            // Custom validation: either consumerPid or callbackAddress must be present
            if ((message.getConsumerPid() == null && message.getCallbackAddress() == null) ||
                    (message.getConsumerPid() != null && message.getCallbackAddress() != null)) {
                messages.add("either providerPid or callbackAddress must be present");
            }

            if (messages.isEmpty()) {
                return message;
            }
            throw new ValidationException("ContractOfferMessage - " + String.join(", ", messages));
        }
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    @JsonProperty(value = DSpaceConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return ContractOfferMessage.class.getSimpleName();
    }
}
