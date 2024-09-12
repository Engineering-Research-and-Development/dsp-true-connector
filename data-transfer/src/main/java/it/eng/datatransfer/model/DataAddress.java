package it.eng.datatransfer.model;

import java.io.Serializable;
import java.util.List;
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
@JsonDeserialize(builder = DataAddress.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DataAddress implements Serializable {

	private static final long serialVersionUID = -2851504722128056767L;

	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_ENDPOINT_TYPE)
	private String endpointType;
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_ENDPOINT)
	private String endpoint;
	
	@JsonProperty(DSpaceConstants.DSPACE_ENDPOINT_PROPERTIES)
	private List<EndpointProperty> endpointProperties;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private DataAddress dataAddress;
		
		private Builder() {
			dataAddress = new DataAddress();
		}
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_ENDPOINT_TYPE)
		public Builder endpointType(String endpointType) {
			dataAddress.endpointType = endpointType;
			return this;
		}

		@JsonProperty(DSpaceConstants.DSPACE_ENDPOINT)
		public Builder endpoint(String endpoint) {
			dataAddress.endpoint = endpoint;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_ENDPOINT_PROPERTIES)
		public Builder endpointProperties(List<EndpointProperty> endpointProperties) {
			dataAddress.endpointProperties = endpointProperties;
			return this;
		}
		
		public DataAddress build() {
			Set<ConstraintViolation<DataAddress>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(dataAddress);
			if(violations.isEmpty()) {
				return dataAddress;
			}
			throw new ValidationException("DataAddress - " + 
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
	}	

	@JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
	public String getType() {
		return DSpaceConstants.DSPACE + DataAddress.class.getSimpleName();
	}
}
