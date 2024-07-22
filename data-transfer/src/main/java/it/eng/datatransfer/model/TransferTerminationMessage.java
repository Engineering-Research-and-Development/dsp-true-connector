package it.eng.datatransfer.model;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * {
  "@context":  "https://w3id.org/dspace/2024/1/context.json",
  "@type": "dspace:TransferTerminationMessage",
  "dspace:providerPid": "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab",
  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
  "dspace:code": "...",
  "dspace:reason": [
    {},
    {}
  ]
}
 */

@Getter
@JsonDeserialize(builder = TransferTerminationMessage.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransferTerminationMessage extends AbstractTransferMessage {

	private static final long serialVersionUID = 7638790814588039703L;

	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_PROVIDER_PID)
	private String providerPid;
	
	@JsonProperty(DSpaceConstants.DSPACE_CODE)
	private String code;
	
	@JsonProperty(DSpaceConstants.DSPACE_REASON)
	private List<Object> reason;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private TransferTerminationMessage message;
		
		private Builder() {
			message = new TransferTerminationMessage();
		}
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		@JsonSetter(DSpaceConstants.DSPACE_CONSUMER_PID)
		public Builder consumerPid(String consumerPid) {
			message.consumerPid = consumerPid;
			return this;
		}

		@JsonSetter((DSpaceConstants.DSPACE_PROVIDER_PID))
		public Builder providerPid(String providerPid) {
			message.providerPid = providerPid;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_CODE)
		public Builder code(String code) {
			message.code = code;
			return this;
		}
		@JsonProperty(DSpaceConstants.DSPACE_REASON)
		public Builder reason(List<Object> reason) {
			message.reason = reason;
			return this;
		}

		public TransferTerminationMessage build() {
			Set<ConstraintViolation<TransferTerminationMessage>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(message);
			if(violations.isEmpty()) {
				return message;
			}
			throw new ValidationException(
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
	}
	
	@Override
	public String getType() {
		return DSpaceConstants.DSPACE + TransferTerminationMessage.class.getSimpleName();
	}

}
