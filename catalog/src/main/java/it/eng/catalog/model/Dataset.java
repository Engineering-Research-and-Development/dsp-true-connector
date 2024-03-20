package it.eng.catalog.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = Dataset.Builder.class)
@JsonPropertyOrder(value = { DSpaceConstants.CONTEXT, DSpaceConstants.ID, DSpaceConstants.TYPE, DSpaceConstants.DCT_TITLE,
		DSpaceConstants.DCT_DESCRIPTION, DSpaceConstants.DCAT_KEYWORD, DSpaceConstants.ODRL_HAS_POLICY, 
		DSpaceConstants.DCAT_DISTRIBUTION}, alphabetic = true)
public class Dataset {

	@JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
	private String context = DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE;
	
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

	@NotNull
	@JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
	private List<Offer> hasPolicy;
	@JsonProperty(DSpaceConstants.DCAT_DISTRIBUTION)
	private List<Distribution> distribution;

	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		private final Dataset dataset;

		private Builder() {
			dataset = new Dataset();
		}

		@JsonCreator
		public static Builder newInstance() {
			return new Builder();
		}
		
		@JsonProperty(DSpaceConstants.ID)
		public Builder id(String id) {
			dataset.id = id;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCAT_KEYWORD)
		public Builder keyword(List<String> keyword) {
			dataset.keyword = keyword;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCAT_THEME)
		public Builder theme(List<String> theme) {
			dataset.theme = theme;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_CONFORMSTO)
		public Builder conformsTo(String conformsTo) {
			dataset.conformsTo = conformsTo;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_CREATOR)
		public Builder creator(String creator) {
			dataset.creator = creator;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
		public Builder description(List<Multilanguage> description) {
			dataset.description = description;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_IDENTIFIER)
		public Builder identifier(String identifier) {
			dataset.identifier = identifier;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_ISSUED)
		public Builder issued(String issued) {
			dataset.issued = issued;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_MODIFIED)
		public Builder modified(String modified) {
			dataset.modified = modified;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_TITLE)
		public Builder title(String title) {
			dataset.title = title;
			return this;
		}

		@JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
		public Builder hasPolicy(List<Offer> policies) {
			dataset.hasPolicy = policies;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DCAT_DISTRIBUTION)
		public Builder distribution(List<Distribution> distribution) {
			dataset.distribution = distribution;
			return this;
		}

		public Dataset build() {
			if(dataset.id == null) {
				dataset.id = UUID.randomUUID().toString();
			}
			Set<ConstraintViolation<Dataset>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(dataset);
			if(violations.isEmpty()) {
				return dataset;
			}
			throw new ValidationException(
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
	}

	@JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
	public String getType() {
		return DSpaceConstants.DCAT + Dataset.class.getSimpleName();
	}
}
