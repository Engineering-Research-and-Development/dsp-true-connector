package it.eng.negotiation.model;

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
  "@type": "dspace:ContractAgreementMessage",
  "dspace:providerPid": "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab",
  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
  "dspace:agreement": {
    "@id": "urn:uuid:e8dc8655-44c2-46ef-b701-4cffdc2faa44",
    "@type": "odrl:Agreement",
    "odrl:target": "urn:uuid:3dd1add4-4d2d-569e-d634-8394a8836d23",
    "dspace:timestamp": "2023-01-01T01:00:00Z",
    "odrl:assigner": "urn:tsdshhs636378",
    "odrl:assignee": "urn:jashd766",
    "odrl:permission": [{
      "odrl:action": "odrl:use" ,
      "odrl:constraint": [{
        "odrl:leftOperand": "odrl:dateTime",
        "odrl:operand": "odrl:lteq",
        "odrl:rightOperand": { "@value": "2023-12-31T06:00Z", "@type": "xsd:dateTime" }
      }]
    }]
  },
  "dspace:callbackAddress": "https://......"
}
"required": [ "@context", "@type", "dspace:providerPid", "dspace:consumerPid", "dspace:agreement", "dspace:callbackAddress"] 
 */

@Getter
@JsonDeserialize(builder = ContractAgreementMessage.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic =  true) 
public class ContractAgreementMessage extends AbstractNegotiationObject {
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
	private String consumerPid;
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_CALLBACK_ADDRESS)
	private String callbackAddress;
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_AGREEMENT)
	private Agreement agreement;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private ContractAgreementMessage message;
		
		private Builder() {
			message = new ContractAgreementMessage();
		}
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
		public Builder consumerPid(String consumerPid) {
			message.consumerPid = consumerPid;
			return this;
		}
		
		@JsonProperty((DSpaceConstants.DSPACE_PROVIDER_PID))
		public Builder providerPid(String providerPid) {
			message.providerPid = providerPid;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_CALLBACK_ADDRESS)
		public Builder callbackAddress(String callbackAddress) {
			message.callbackAddress = callbackAddress;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_AGREEMENT)		
		public Builder agreement(Agreement agreement) {
			message.agreement = agreement;
			return this;
		}
		
		public ContractAgreementMessage build() {
			Set<ConstraintViolation<ContractAgreementMessage>> violations = Validation.buildDefaultValidatorFactory().getValidator().validate(message);
			if(violations.isEmpty()) {
				return message;
			}
			throw new ValidationException("ContractAgreementMessage - " + 
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(", ")));
		}
	}

	@Override
	@JsonIgnoreProperties(value={ "type" }, allowGetters=true)
	public String getType() {
		return DSpaceConstants.DSPACE + ContractAgreementMessage.class.getSimpleName();
	}
	
}
