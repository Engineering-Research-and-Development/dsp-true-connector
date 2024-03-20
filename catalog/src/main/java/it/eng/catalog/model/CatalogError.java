package it.eng.catalog.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import it.eng.tools.model.DSpaceConstants;
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
public class CatalogError extends AbstractCatalogMessage {

	@JsonProperty(DSpaceConstants.DSPACE_CODE)
	private String code;
	@JsonProperty(DSpaceConstants.DSPACE_REASON)
	private List<Reason> reason;
	
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
			return catalogError;
		}
	}

	@Override
	public String getType() {
		return DSpaceConstants.DSPACE + CatalogError.class.getSimpleName();
	}
}
