package it.eng.negotiation.model;

import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.ToStringBuilder;

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
  "@type": "dspace:ContractOfferMessage",
  "dspace:providerPid": "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab",
  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
  "dspace:offer": {
    "@type": "odrl:Offer",
    "@id": "urn:uuid:6bcea82e-c509-443d-ba8c-8eef25984c07",
    "odrl:target": "urn:uuid:3dd1add8-4d2d-569e-d634-8394a8836a88",
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

"required": [ "@context", "@type", "dspace:providerPid", "dspace:offer", "dspace:callbackAddress" ]
 */

@Getter
@JsonDeserialize(builder = ContractOfferMessage.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic =  true) 
public class ContractOfferMessage extends AbstractNegotiationObject {

	// not mandatory in initial offer message
	@JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
	private String consumerPid;
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_OFFER)
	private Offer offer;
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_CALLBACK_ADDRESS)
	private String callbackAddress;

	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {

		private ContractOfferMessage message;
		
		private Builder() {
			message = new ContractOfferMessage();
		}
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		@JsonProperty((DSpaceConstants.DSPACE_CONSUMER_PID))
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
		
		@JsonProperty(DSpaceConstants.DSPACE_OFFER)
		public Builder offer(Offer offer) {
			message.offer = offer;
			return this;
		}
		
		public ContractOfferMessage build() {
			Set<ConstraintViolation<ContractOfferMessage>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(message);
			if(violations.isEmpty()) {
				return message;
			}
			throw new ValidationException("ContractOfferMessage - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(", ")));
			}
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this);
		}

	@Override
	public String getType() {
		return DSpaceConstants.DSPACE + ContractOfferMessage.class.getSimpleName();
	}
}
