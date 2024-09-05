package it.eng.datatransfer.model;

import java.io.Serializable;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
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

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = EndpointProperty.Builder.class)
public class EndpointProperty implements Serializable {

	private static final long serialVersionUID = -1709802825239302678L;
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_NAME)
	private String name;
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_VALUE)
	private String value;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private EndpointProperty message;
		
		private Builder() {
			message = new EndpointProperty();
		}
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_NAME)
		public Builder name(String name) {
			message.name = name;
			return this;
		}

		@JsonProperty(DSpaceConstants.DSPACE_VALUE)
		public Builder value(String value) {
			message.value = value;
			return this;
		}
		
		public EndpointProperty build() {
			Set<ConstraintViolation<EndpointProperty>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(message);
			if(violations.isEmpty()) {
				return message;
			}
			throw new ValidationException("EndpointProperty - " + 
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
	}	
		 
	@JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
	public String getType() {
		return DSpaceConstants.DSPACE + EndpointProperty.class.getSimpleName();
	}
}
