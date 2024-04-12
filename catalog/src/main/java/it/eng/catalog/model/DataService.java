package it.eng.catalog.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = DataService.Builder.class)
@JsonPropertyOrder(value = { "@id", "@type" }, alphabetic = true)
public class DataService {

	@JsonProperty(DSpaceConstants.ID)
	private String id;
	// Resource
	@JsonProperty(DSpaceConstants.DCAT_KEYWORD)
	private List<String> keyword;
	@JsonProperty(DSpaceConstants.DCAT_THEME)
	private List<String> theme;
	@JsonProperty(DSpaceConstants.DCT_CONFORMSTO)
	private String conformsTo;
	@JsonProperty(DSpaceConstants.DCT_CREATOR)
	private String creator;
	@JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
	private List<Multilanguage> description;
	@JsonProperty(DSpaceConstants.DCT_IDENTIFIER)
	private String identifier;
	@JsonProperty(DSpaceConstants.DCT_ISSUED)
	private String issued;
	@JsonProperty(DSpaceConstants.DCT_MODIFIED)
	private String modified;
	@JsonProperty(DSpaceConstants.DCT_TITLE)
	private String title;

	@JsonProperty(DSpaceConstants.DCAT_ENDPOINT_DESCRIPTION)
	private String endpointDescription;
	@JsonProperty(DSpaceConstants.DCAT_ENDPOINT_URL)
	private String endpointURL;
	@JsonProperty(DSpaceConstants.DCAT_SERVES_DATASET)
	private List<Dataset> servesDataset;

	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		private DataService service;

		private Builder() {
			service = new DataService();
		}

		@JsonCreator
		public static Builder newInstance() {
			return new Builder();
		}
		
		@JsonProperty(DSpaceConstants.ID)
		public Builder id(String id) {
			service.id = id;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCAT_KEYWORD)
		public Builder keyword(List<String> keyword) {
			service.keyword = keyword;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCAT_THEME)
		public Builder theme(List<String> theme) {
			service.theme = theme;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_CONFORMSTO)
		public Builder conformsTo(String conformsTo) {
			service.conformsTo = conformsTo;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_CREATOR)
		public Builder creator(String creator) {
			service.creator = creator;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
		public Builder description(List<Multilanguage> description) {
			service.description = description;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_IDENTIFIER)
		public Builder identifier(String identifier) {
			service.identifier = identifier;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_ISSUED)
		public Builder issued(String issued) {
			service.issued = issued;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_MODIFIED)
		public Builder modified(String modified) {
			service.modified = modified;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_TITLE)
		public Builder title(String title) {
			service.title = title;
			return this;
		}
/* ************ */
		
		@JsonProperty(DSpaceConstants.DCAT_ENDPOINT_DESCRIPTION)
		public Builder endpointDescription(String endpointDescription) {
			service.endpointDescription = endpointDescription;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DCAT_ENDPOINT_URL)
		public Builder endpointURL(String endpointURL) {
			service.endpointURL = endpointURL;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DCAT_SERVES_DATASET)
		public Builder servesDataset(List<Dataset> servesDataset) {
			service.servesDataset = servesDataset;
			return this;
		}
		
		public DataService build() {
			if(service.id == null) {
				service.id = UUID.randomUUID().toString();
			}
			Set<ConstraintViolation<DataService>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(service);
			if(violations.isEmpty()) {
				return service;
			}
			throw new ValidationException("DataService - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
	}
	
	@JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
	public String getType() {
		return DSpaceConstants.DCAT + DataService.class.getSimpleName();
	}
}
