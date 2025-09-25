package it.eng.datatransfer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
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
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = TransferError.Builder.class)
public class TransferError extends AbstractTransferMessage {

	private static final long serialVersionUID = 8503165742320963612L;

	@NotNull
	private String providerPid;
	
	private String code;
	
	private List<Object> reason;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {

		private TransferError message;
		
		private Builder() {
			message = new TransferError();
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
		
		public Builder code(String code) {
			message.code = code;
			return this;
		}
		public Builder reason(List<Object> reason) {
			message.reason = reason;
			return this;
		}
		
		public TransferError build() {
			Set<ConstraintViolation<TransferError>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(message);
			if(violations.isEmpty()) {
				return message;
			}
			throw new ValidationException("TransferError - " +
						violations
							.stream()
							.map(v -> v.getPropertyPath() + " " + v.getMessage())
							.collect(Collectors.joining(",")));
			}
		}

	@Override
	public String getType() {
		return TransferError.class.getSimpleName();
	}
}
