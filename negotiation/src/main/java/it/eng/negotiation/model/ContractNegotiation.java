package it.eng.negotiation.model;

import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
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
    "@type": "dspace:ContractNegotiation",
    "dspace:providerPid": "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab",
    "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
    "dspace:state": "dspace:REQUESTED"
  }
"required": [ "@context", "@type", "dspace:providerPid", "dspace:consumerPid", "dspace:state" ]
 */

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = ContractNegotiation.Builder.class)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic = true)
public class ContractNegotiation extends AbstractNegotiationModel {

    @NotNull
    @JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
    private String consumerPid;

    @NotNull
    @JsonProperty(DSpaceConstants.DSPACE_STATE)
    private ContractNegotiationState state;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final ContractNegotiation message;

        private Builder() {
            message = new ContractNegotiation();
        }

        @JsonCreator
        public static ContractNegotiation.Builder newInstance() {
            return new ContractNegotiation.Builder();
        }

        @JsonProperty(DSpaceConstants.DSPACE_STATE)
        public Builder state(ContractNegotiationState state) {
            message.state = state;
            return this;
        }

        @JsonSetter(DSpaceConstants.DSPACE_PROVIDER_PID)
        public Builder providerPid(String providerPid) {
            message.providerPid = providerPid;
            return this;
        }

        @JsonSetter(DSpaceConstants.DSPACE_CONSUMER_PID)
        public Builder consumerPid(String consumerPid) {
            message.consumerPid = consumerPid;
            return this;
        }

        public ContractNegotiation build() {
            if (message.providerPid == null) {
                message.providerPid = message.createNewId();
            }
            Set<ConstraintViolation<ContractNegotiation>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(message);
            if (violations.isEmpty()) {
                return message;
            }
            throw new ValidationException(
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(", ")));
        }
    }

    @Override
    public String toString() {
        return ToStringBuilder.getDefaultStyle().toString();
    }

    @Override
    @JsonIgnoreProperties(value = {"type"}, allowGetters = true)
    public String getType() {
        return DSpaceConstants.DSPACE + ContractNegotiation.class.getSimpleName();
    }
}

