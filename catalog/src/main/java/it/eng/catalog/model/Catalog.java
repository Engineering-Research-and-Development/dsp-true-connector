package it.eng.catalog.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@JsonDeserialize(builder = Catalog.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {DSpaceConstants.CONTEXT, DSpaceConstants.ID, DSpaceConstants.TYPE, DSpaceConstants.DCT_TITLE,
        DSpaceConstants.DCT_DESCRIPTION, DSpaceConstants.DSPACE_PARTICIPANT_ID, DSpaceConstants.DCAT_KEYWORD,
        DSpaceConstants.DCAT_SERVICE, DSpaceConstants.DCAT_DATASET}, alphabetic = true)
@Document(collection = "catalogs")
public class Catalog {


    @JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
    private String context = DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE;

    @JsonProperty(DSpaceConstants.ID)
    @Id
    private String id;

    // from Dataset
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
    
    // from Dataset definition
    @JsonProperty(DSpaceConstants.DCAT_DISTRIBUTION)
    private List<Distribution> distribution;
    // assumption - policy for allowing catalog usage/display - not mandatory for catalog
	@JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
	private List<Offer> hasPolicy;
	// end Dataset definition
	
    @JsonProperty(DSpaceConstants.DCAT_DATASET)
    private List<Dataset> dataset;
    @JsonProperty(DSpaceConstants.DCAT_SERVICE)
    private List<DataService> service;

    @JsonProperty(DSpaceConstants.DSPACE_PARTICIPANT_ID)
    private String participantId;

    @JsonProperty("foaf:homepage")
    private String homepage;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private Catalog catalog;

        private Builder() {
            catalog = new Catalog();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty(DSpaceConstants.ID)
        public Builder id(String id) {
            catalog.id = id;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_KEYWORD)
        public Builder keyword(List<String> keyword) {
            catalog.keyword = keyword;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_THEME)
        public Builder theme(List<String> theme) {
            catalog.theme = theme;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_CONFORMSTO)
        public Builder conformsTo(String conformsTo) {
            catalog.conformsTo = conformsTo;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_CREATOR)
        public Builder creator(String creator) {
            catalog.creator = creator;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
        public Builder description(List<Multilanguage> description) {
            catalog.description = description;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_IDENTIFIER)
        public Builder identifier(String identifier) {
            catalog.identifier = identifier;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_ISSUED)
        public Builder issued(String issued) {
            catalog.issued = issued;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_MODIFIED)
        public Builder modified(String modified) {
            catalog.modified = modified;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_TITLE)
        public Builder title(String title) {
            catalog.title = title;
            return this;
        }
        
        @JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
		public Builder hasPolicy(List<Offer> policies) {
        	catalog.hasPolicy = policies;
			return this;
		}

        @JsonProperty(DSpaceConstants.DCAT_DISTRIBUTION)
        public Builder distribution(List<Distribution> distribution) {
            catalog.distribution = distribution;
            return this;
        }
        //**********


        @JsonProperty(DSpaceConstants.DCAT_DATASET)
        public Builder dataset(List<Dataset> datasets) {
            catalog.dataset = datasets;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_SERVICE)
        public Builder service(List<DataService> service) {
            catalog.service = service;
            return this;
        }

        @JsonProperty(DSpaceConstants.DSPACE_PARTICIPANT_ID)
        public Builder participantId(String participantId) {
            catalog.participantId = participantId;
            return this;
        }

        @JsonProperty("foaf:homepage")
        public Builder homepage(String homepage) {
            catalog.homepage = homepage;
            return this;
        }

        public Catalog build() {
            if (catalog.id == null) {
                catalog.id = UUID.randomUUID().toString();
            }
            Set<ConstraintViolation<Catalog>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(catalog);
			if(violations.isEmpty()) {
				return catalog;
			}
			throw new ValidationException("Catalog - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
    }

    @JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
    public String getType() {
        return DSpaceConstants.DCAT + Catalog.class.getSimpleName();
    }
}
