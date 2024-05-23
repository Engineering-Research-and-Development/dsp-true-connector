package it.eng.catalog.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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

@Getter
@JsonDeserialize(builder = Offer.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic =  true) 
public class Offer {

	/*
	"odrl:offer": {
    "@type": "odrl:Offer",
    "@id": "urn:uuid:6bcea82e-c509-443d-ba8c-8eef25984c07",
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
//	@NotNull (if new offer from consumer Id of offer is null)
	@JsonProperty(DSpaceConstants.ID)
	private String id;
	
	// Different to a Catalog or Dataset, the Offer inside a Contract Request Message must have an odrl:target attribute.
	// not mandatory for Catalog or Dataset offer to have target field - different from the Offer in negotiation module
	@JsonProperty(DSpaceConstants.ODRL_TARGET)
	private String target;
	
	// required in catalog???
	@JsonProperty(DSpaceConstants.ODRL_ASSIGNER)
	private String assigner;

	// required in catalog???
	@JsonProperty(DSpaceConstants.ODRL_ASSIGNEE)
	private String assignee;
	
	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_PERMISSION)
	private List<Permission> permission;
	
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

		@JsonSetter(DSpaceConstants.ID)
		public Builder id(String id) {
			offer.id = id;
			return this;
		}
		
		@JsonSetter(DSpaceConstants.ODRL_TARGET)
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
		
		@JsonSetter(DSpaceConstants.ODRL_PERMISSION)
		public Builder permission(List<Permission> permission) {
			offer.permission = permission;
			return this;
		}
		
		public Offer build() {
			if (offer.id == null) {
				offer.id = UUID.randomUUID().toString();
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
