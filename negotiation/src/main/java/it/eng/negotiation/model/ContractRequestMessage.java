package it.eng.negotiation.model;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
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

/* Sample
	{
	  "@context":  "https://w3id.org/dspace/v0.8/context.json",
	  "@type": "dspace:ContractRequestMessage",
	  "dspace:dataset": "urn:uuid:3dd1add8-4d2d-569e-d634-8394a8836a88",
	  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
	  "dspace:providerPid": "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab",
	  "dspace:offer": {
	    "@type": "odrl:Offer",
	    "@id": "urn:uuid:2828282:3dd1add8-4d2d-569e-d634-8394a8836a89",
	    "odrl:target": "urn:uuid:3dd1add8-4d2d-569e-d634-8394a8836a88"
	  },
	  "dspace:callbackAddress": "https://......"
	}
"required": [ "@context", "@type", "dspace:consumerPid", "dspace:offer", "dspace:callbackAddress" ]	
 */

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = ContractRequestMessage.Builder.class)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic =  true) 
public class ContractRequestMessage {
// does not extends AbstractNegotiationModel because providerPid is not mandatory
	
	@JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
	private String context = DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE;

	@JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
	public String type = DSpaceConstants.DSPACE + ContractRequestMessage.class.getSimpleName();

	@JsonProperty(DSpaceConstants.DSPACE_PROVIDER_PID)
	private String providerPid;

	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
	private String consumerPid;

	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_CALLBACK_ADDRESS)
	private String callbackAddress;

	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_OFFER)
	private Offer offer;
	
	@JsonPOJOBuilder(withPrefix = "", buildMethodName = "build")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private ContractRequestMessage message;
		
		private Builder() {
			message = new ContractRequestMessage();
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
		
		@JsonProperty(DSpaceConstants.DSPACE_OFFER)
		public Builder offer(Offer offer) {
			message.offer = offer;
			return this;
		}
		
		public ContractRequestMessage build() {
			Set<ConstraintViolation<ContractRequestMessage>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(message);
			if(violations.isEmpty()) {
				return message;
			}
			throw new ValidationException("ContractRequestMessage - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(", ")));
			}
	}

	@JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
	@JsonIgnoreProperties(value={ "type" }, allowGetters=true)
	public String getType() {
		return DSpaceConstants.DSPACE + ContractRequestMessage.class.getSimpleName();
	}
		
}
