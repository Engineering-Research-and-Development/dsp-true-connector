package it.eng.negotiation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
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
@JsonDeserialize(builder = ContractNegotiationErrorMessage.Builder.class)
public class ContractNegotiationErrorMessage extends AbstractNegotiationObject {

    @NotNull
    private String consumerPid;

    private String code;

    private List<String> reason;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final ContractNegotiationErrorMessage message;

        private Builder() {
            message = new ContractNegotiationErrorMessage();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder providerPid(String providerPid) {
            message.providerPid = providerPid;
            return this;
        }

        public Builder consumerPid(String consumerPid) {
            message.consumerPid = consumerPid;
            return this;
        }

        public Builder code(String code) {
            message.code = code;
            return this;
        }

        public Builder reason(List<String> reason) {
            message.reason = reason;
            return this;
        }

        public ContractNegotiationErrorMessage build() {
            if (message.consumerPid == null) {
                message.consumerPid = message.createNewPid();
            }
            Set<ConstraintViolation<ContractNegotiationErrorMessage>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(message);
            if (violations.isEmpty()) {
                return message;
            }
            throw new ValidationException("ContractNegotiationErrorMessage - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(", ")));
        }
    }

    @Override
    @JsonProperty(value = DSpaceConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return ContractNegotiationErrorMessage.class.getSimpleName();
    }
}
