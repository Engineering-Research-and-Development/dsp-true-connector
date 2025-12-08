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

import java.util.Set;
import java.util.stream.Collectors;


@Getter
@JsonDeserialize(builder = ContractAgreementVerificationMessage.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ContractAgreementVerificationMessage extends AbstractNegotiationObject {

    @NotNull
    private String consumerPid;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final ContractAgreementVerificationMessage message;

        private Builder() {
            message = new ContractAgreementVerificationMessage();
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

        public ContractAgreementVerificationMessage build() {
            Set<ConstraintViolation<ContractAgreementVerificationMessage>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(message);
            if (violations.isEmpty()) {
                return message;
            }
            throw new ValidationException("ContractAgreementVerificationMessage - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(", ")));
        }
    }

    @Override
    @JsonProperty(value = DSpaceConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return ContractAgreementVerificationMessage.class.getSimpleName();
    }

}
