package it.eng.negotiation.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.mapping.Document;

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

@Getter
@JsonDeserialize(builder = Agreement.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic =  true)
@Document(collection = "agreements")
public class Agreement {

	/*
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
  }
  
  "required": ["@type",	"@id", "@target", "odrl:assignee", "odrl:assigner"]
	 */
	@NotNull
	@JsonProperty(DSpaceConstants.ID)
	private String id;
	
	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_ASSIGNER)
	private String assigner;
	
	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_ASSIGNEE)
	private String assignee;
	
	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_TARGET)
	private String target;
	
	@JsonProperty(DSpaceConstants.DSPACE_TIMESTAMP)
	private String timestamp;
	
	@JsonProperty(DSpaceConstants.ODRL_PERMISSION)
	private List<Permission> permission;

	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {

		private Agreement agreement;
		
		private Builder() {
			agreement = new Agreement();
		}
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		@JsonProperty(DSpaceConstants.ID)
		public Builder id(String id) {
			agreement.id = id;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_ASSIGNER)
		public Builder assigner(String assigner) {
			agreement.assigner = assigner;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_ASSIGNEE)
		public Builder assignee(String assignee) {
			agreement.assignee = assignee;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_TARGET)
		public Builder target(String target) {
			agreement.target = target;
			return this;
		}

		@JsonProperty(DSpaceConstants.DSPACE_TIMESTAMP)
		public Builder timestamp(String timestamp) {
			agreement.timestamp = timestamp;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_PERMISSION)
		public Builder permission(List<Permission> permission) {
			agreement.permission = permission;
			return this;
		}
		
		public Agreement build() {
			if (agreement.id == null) {
				agreement.id = "urn:uuid:" + UUID.randomUUID().toString();
			}
			Set<ConstraintViolation<Agreement>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(agreement);
			if(violations.isEmpty()) {
				return agreement;
			}
			throw new ValidationException("Agreement - " + 
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(", ")));
			}
	}

	@JsonIgnoreProperties(value={ "type" }, allowGetters=true)
	public String getType() {
		return DSpaceConstants.DSPACE + Agreement.class.getSimpleName();
	}
		
}
