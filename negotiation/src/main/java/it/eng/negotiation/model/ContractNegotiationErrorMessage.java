package it.eng.negotiation.model;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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

/*
{
  "@context":  "https://w3id.org/dspace/2024/1/context.json",
  "@type": "dspace:ContractNegotiationError",
  "dspace:providerPid": "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab",
  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
  "dspace:code": "...",
  "dspace:reason": [
    {
      "@value": "Catalog not provisioned for this requester.",
      "@language": "en"
    }
  ],
  "dct:description": [
    {
      "@language": "en",
      "@value": "The parsing of the input parameters failed."
    },
    {
      "@language": "de",
      "@value": "Das Lesen der Eingabeparameter schlug fehl."
    }
  ]
}
"required": [ "@context", "@type", "dspace:providerPid", "dspace:consumerPid"]
 */

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = ContractNegotiationErrorMessage.Builder.class)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic = true)
public class ContractNegotiationErrorMessage extends AbstractNegotiationObject {

    @NotNull
    @JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
    private String consumerPid;

    @JsonProperty(DSpaceConstants.DSPACE_CODE)
    private String code;
    
    @JsonProperty(DSpaceConstants.DSPACE_REASON)
    private List<Reason> reason;
    
    @JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
    private List<Description> description;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private ContractNegotiationErrorMessage message;

        private Builder() {
            message = new ContractNegotiationErrorMessage();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty((DSpaceConstants.DSPACE_PROVIDER_PID))
        public Builder providerPid(String providerPid) {
            message.providerPid = providerPid;
            return this;
        }

        @JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
        public Builder consumerPid(String consumerPid) {
            message.consumerPid = consumerPid;
            return this;
        }

        @JsonProperty(DSpaceConstants.DSPACE_CODE)
        public Builder code(String code) {
            message.code = code;
            return this;
        }

        @JsonProperty(DSpaceConstants.DSPACE_REASON)
        public Builder reason(List<Reason> reason) {
            message.reason = reason;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
        public Builder description(List<Description> description) {
            message.description = description;
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
    public String getType() {
        return DSpaceConstants.DSPACE + ContractNegotiationErrorMessage.class.getSimpleName();
    }

}
