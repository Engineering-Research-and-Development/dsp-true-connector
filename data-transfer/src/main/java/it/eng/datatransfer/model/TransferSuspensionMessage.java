package it.eng.datatransfer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@JsonDeserialize(builder = TransferSuspensionMessage.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {DSpaceConstants.CONTEXT, DSpaceConstants.TYPE}, alphabetic = true)
public class TransferSuspensionMessage extends AbstractTransferMessage {

	private static final long serialVersionUID = 6065245999402269996L;

	@NotNull
	private String providerPid;
	
	private String code;
	
	private List<Object> reason;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private TransferSuspensionMessage message;
		
		private Builder() {
			message = new TransferSuspensionMessage();
		}
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		public Builder consumerPid(String consumerPid) {
			message.consumerPid = consumerPid;
			return this;
		}

		public Builder providerPid(String providerPid) {
			message.providerPid = providerPid;
			return this;
		}
		
		public Builder code(String code) {
			message.code = code;
			return this;
		}
		public Builder reason(List<Object> reason) {
			message.reason = reason;
			return this;
		}

		public TransferSuspensionMessage build() {
			Set<ConstraintViolation<TransferSuspensionMessage>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(message);
			if(violations.isEmpty()) {
				return message;
			}
			throw new ValidationException("TransferSuspensionMessage - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
		}

	
	@Override
	public String getType() {
		return TransferSuspensionMessage.class.getSimpleName();
	}

}
