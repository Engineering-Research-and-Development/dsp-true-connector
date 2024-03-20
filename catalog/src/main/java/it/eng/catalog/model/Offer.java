package it.eng.catalog.model;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.DSpaceConstants;
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
	 */
	@JsonProperty(DSpaceConstants.ID)
	private String id;
	@JsonProperty(DSpaceConstants.ODRL_TARGET)
	private String target;
	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_ASSIGNER)
	private String assigner;
	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_ASSIGNEE)
	private String assignee;
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
			return offer;
		}
	}


//	@JsonIgnore
//	public boolean isBlank() {
//		if(id.isBlank() || target.isBlank()) {
//			return true;
//		}
//		return false;
//	}
		
}
