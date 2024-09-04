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
  "@type": "dspace:ContractNegotiationEventMessage",
  "dspace:providerPid": "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab",
  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
  "dspace:eventType": "dspace:ACCEPTED"
}

"required": [ "@context", "@type", "dspace:providerPid", "dspace:consumerPid", "dspace:eventType" ]
 */

@Getter
@JsonDeserialize(builder = ContractNegotiationEventMessage.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic =  true) 
public class ContractNegotiationEventMessage extends AbstractNegotiationObject {
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
	private String consumerPid;
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_EVENT_TYPE)
	private ContractNegotiationEventType eventType;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private ContractNegotiationEventMessage message;
	
		private Builder() {
			message = new ContractNegotiationEventMessage();
		}

		public static Builder newInstance() {
			return new Builder();
		}

		@JsonProperty((DSpaceConstants.DSPACE_PROVIDER_PID))
		public Builder providerPid(String providerPid) {
			message.providerPid = providerPid;
			return this;
		}
		
		@JsonProperty((DSpaceConstants.DSPACE_CONSUMER_PID))
		public Builder consumerPid(String consumerPid) {
			message.consumerPid = consumerPid;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_EVENT_TYPE)
		public Builder eventType(ContractNegotiationEventType eventType) {
			message.eventType = eventType;
			return this;
		}
		
		public ContractNegotiationEventMessage build() {
			Set<ConstraintViolation<ContractNegotiationEventMessage>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(message);
			if(violations.isEmpty()) {
				return message;
			}
			throw new ValidationException("ContractNegotiationEventMessage - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(", ")));
			}
	}

	@Override
	public String getType() {
		return DSpaceConstants.DSPACE + ContractNegotiationEventMessage.class.getSimpleName();
	}
		
}
