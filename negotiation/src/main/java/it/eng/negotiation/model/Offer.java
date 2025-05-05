package it.eng.negotiation.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
	"odrl:offer": {
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
  }
  Offer -> allOf  /definitions/MessageOffer
		allOf /definitions/PolicyClass
			allOf /definitions/AbstractPolicyRule
				"not": { "required": [ "odrl:target" ] }
			"required": "@id"
		"required": [ "@type", "odrl:assigner" ]
	"required": "odrl:permission" or "odrl:prohibition"
   	"not": { "required": [ "odrl:target" ] }
 *
 */

@Getter
@EqualsAndHashCode
@JsonDeserialize(builder = Offer.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic =  true) 
public class Offer {
	
//	@NotNull
	@JsonProperty(DSpaceConstants.ID)
	private String id;

	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_TARGET)
	private String target;
	
	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_ASSIGNER)
	private String assigner;
	
	@JsonProperty(DSpaceConstants.ODRL_ASSIGNEE)
	private String assignee;
	
//	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_PERMISSION)
	private List<Permission> permission;
	
	/**
	 * The original ID as in the provider's Catalog.
	 */
	@JsonIgnore
	private String originalId;
	
	@JsonIgnoreProperties(value={ "type" }, allowGetters=true)
	@JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
	private String getType() {
		return DSpaceConstants.ODRL + Offer.class.getSimpleName();
	}
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private Offer offer;

		private Builder() {
			offer = new Offer();
		}

		public static Builder newInstance() {
			return new Builder();
		}

		@JsonProperty(DSpaceConstants.ID)
		public Builder id(String id) {
			offer.id = id;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_TARGET)
		public Builder target(String target) {
			offer.target = target;
			return this;
		}

		@JsonProperty(DSpaceConstants.ODRL_ASSIGNER)
		public Builder assigner(String assigner) {
			offer.assigner = assigner;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_ASSIGNEE)
		public Builder assignee(String assignee) {
			offer.assignee = assignee;
			return this;
		}
		
		public Builder originalId(String originalId) {
			offer.originalId = originalId;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_PERMISSION)
		public Builder permission(List<Permission> permission) {
			offer.permission = permission;
			return this;
		}
		
		public Offer build() {
			if (offer.id == null) {
				offer.id = "urn:uuid:" + UUID.randomUUID().toString();
			}
			Set<ConstraintViolation<Offer>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(offer);
			if(violations.isEmpty()) {
				return offer;
			}
			throw new ValidationException("Offer - " + 
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(", ")));
			}
	}
}
