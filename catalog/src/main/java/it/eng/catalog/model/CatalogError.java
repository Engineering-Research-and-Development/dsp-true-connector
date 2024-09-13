package it.eng.catalog.model;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 *
{
  "@context":  "https://w3id.org/dspace/2024/1/context.json",
  "@type": "dspace:CatalogError",
  "dspace:code": "123:A",
  "dspace:reason": [
    { 
      "@value": "Catalog not provisioned for this requester.", 
      "@language": "en"
    }
  ]
}
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = CatalogError.Builder.class)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic =  true)
public class CatalogError extends AbstractCatalogObject {

	private static final long serialVersionUID = -5538644369452254847L;

	@JsonProperty(DSpaceConstants.DSPACE_CODE)
	private String code;
	@JsonProperty(DSpaceConstants.DSPACE_REASON)
	private List<Reason> reason;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		private final CatalogError catalogError;

		private Builder() {
			catalogError = new CatalogError();
		}
		
		@JsonCreator
		public static CatalogError.Builder newInstance() {
			return new CatalogError.Builder();
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_CODE)
		public Builder code(String code) {
			catalogError.code = code;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_REASON)
		public Builder reason(List<Reason> reason) {
			catalogError.reason = reason;
			return this;
		}
		
		public CatalogError build() {
			Set<ConstraintViolation<CatalogError>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(catalogError);
			if(violations.isEmpty()) {
				return catalogError;
			}
			throw new ValidationException("CatalogError - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
	}

	@Override
	public String getType() {
		return DSpaceConstants.DSPACE + CatalogError.class.getSimpleName();
	}
}
