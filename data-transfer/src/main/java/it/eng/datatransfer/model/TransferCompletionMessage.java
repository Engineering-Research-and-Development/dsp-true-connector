package it.eng.datatransfer.model;

import java.util.Set;
import java.util.stream.Collectors;

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

/*
 * {
  "@context":  "https://w3id.org/dspace/2024/1/context.json",
  "@type": "dspace:TransferCompletionMessage",
  "dspace:providerPid": "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab",
  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833"
}
 */

@Getter
@JsonDeserialize(builder = TransferCompletionMessage.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransferCompletionMessage extends AbstractTransferMessage {

	private static final long serialVersionUID = -5101074485122105715L;
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_PROVIDER_PID)
	private String providerPid;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private TransferCompletionMessage message;
		
		private Builder() {
			message = new TransferCompletionMessage();
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

		public TransferCompletionMessage build() {
			Set<ConstraintViolation<TransferCompletionMessage>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(message);
			if(violations.isEmpty()) {
				return message;
			}
			throw new ValidationException("TransferCompletionMessage - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
	}
	
	@Override
	public String getType() {
		return DSpaceConstants.DSPACE + TransferCompletionMessage.class.getSimpleName();
	}
	
}
