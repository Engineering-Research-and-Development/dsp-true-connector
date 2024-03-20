package it.eng.catalog.model;

import java.util.List;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.DSpaceConstants;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = Distribution.Builder.class)
@JsonPropertyOrder(value = { DSpaceConstants.TYPE, DSpaceConstants.DCT_FORMAT, DSpaceConstants.DCAT_ACCESS_SERVICE}
, alphabetic = true)
public class Distribution {

	@JsonProperty(DSpaceConstants.DCT_TITLE)
	private String title;
	@JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
	private List<Multilanguage> description;
	@JsonProperty(DSpaceConstants.DCT_ISSUED)
	private String issued;
	@JsonProperty(DSpaceConstants.DCT_MODIFIED)
	private String modified;
	@JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
	private List<Offer> hasPolicy;
	
	@JsonProperty(DSpaceConstants.DCT_FORMAT)
	private Reference format;
	
	@NonNull
	@JsonProperty(DSpaceConstants.DCAT_ACCESS_SERVICE)
	private List<DataService> dataservice;

	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		private final Distribution distribution;

		private Builder() {
			distribution = new Distribution();
		}

		@JsonCreator
		public static Builder newInstance() {
			return new Builder();
		}
		
		@JsonProperty(DSpaceConstants.DCT_TITLE)
		public Builder title(String title) {
			distribution.title = title;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
		public Builder description(List<Multilanguage> description) {
			distribution.description = description;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DCT_ISSUED)
		public Builder issued(String issued) {
			distribution.issued = issued;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_MODIFIED)
		public Builder modified(String modified) {
			distribution.modified = modified;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DCAT_ACCESS_SERVICE)
		public Builder dataService(List<DataService> dataService) {
			distribution.dataservice = dataService;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DCT_FORMAT)
		public Builder format(Reference format) {
			distribution.format = format;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
		public Builder hasPolicy(List<Offer> policies) {
			distribution.hasPolicy = policies;
			return this;
		}
		
		public Distribution build() {
			return distribution;
		}
	}
	
	@JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
	public String getType() {
		return DSpaceConstants.DSPACE + Distribution.class.getSimpleName();
	}
}
