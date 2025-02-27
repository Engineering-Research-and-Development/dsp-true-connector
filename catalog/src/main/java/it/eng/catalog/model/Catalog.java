package it.eng.catalog.model;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@JsonDeserialize(builder = Catalog.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {DSpaceConstants.CONTEXT, DSpaceConstants.ID, DSpaceConstants.TYPE, DSpaceConstants.DCT_TITLE,
        DSpaceConstants.DCT_DESCRIPTION, DSpaceConstants.DSPACE_PARTICIPANT_ID, DSpaceConstants.DCAT_KEYWORD,
        DSpaceConstants.DCAT_DATASET, DSpaceConstants.DCAT_DISTRIBUTION, DSpaceConstants.DCAT_SERVICE}, alphabetic = true)
@Document(collection = "catalogs")
public class Catalog extends AbstractCatalogObject {

	private static final long serialVersionUID = -7550855731500209188L;

	@JsonProperty(DSpaceConstants.ID)
    @Id
    private String id;

    // from Dataset
    // Resource
    @JsonProperty(DSpaceConstants.DCAT_KEYWORD)
    private Set<String> keyword;
    @JsonProperty(DSpaceConstants.DCAT_THEME)
    private Set<String> theme;
    @JsonProperty(DSpaceConstants.DCT_CONFORMSTO)
    private String conformsTo;
    @JsonProperty(DSpaceConstants.DCT_CREATOR)
    private String creator;
    @JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
    private Set<Multilanguage> description;
    @JsonProperty(DSpaceConstants.DCT_IDENTIFIER)
    private String identifier;
    @JsonProperty(DSpaceConstants.DCT_ISSUED)
    @CreatedDate
    private Instant issued;
    @JsonProperty(DSpaceConstants.DCT_MODIFIED)
    @LastModifiedDate
    private Instant modified;
    @JsonProperty(DSpaceConstants.DCT_TITLE)
    private String title;

    // from Dataset definition
    @JsonProperty(DSpaceConstants.DCAT_DISTRIBUTION)
    @DBRef
    private Set<Distribution> distribution;
    // assumption - policy for allowing catalog usage/display - not mandatory for catalog
    @JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
    private Set<Offer> hasPolicy;
    // end Dataset definition

    @JsonProperty(DSpaceConstants.DCAT_DATASET)
    @DBRef
    private Set<Dataset> dataset;
    @JsonProperty(DSpaceConstants.DCAT_SERVICE)
    @DBRef
    private Set<DataService> service;

    @JsonProperty(DSpaceConstants.DSPACE_PARTICIPANT_ID)
    private String participantId;

    @JsonProperty("foaf:homepage")
    private String homepage;

    @JsonIgnore
    @CreatedBy
    private String createdBy;

    @JsonIgnore
    @LastModifiedBy
    private String lastModifiedBy;

    @JsonIgnore
    @Version
    @Field("version")
    private Long version;

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
        @JsonDeserialize(as = Set.class)
        public Builder keyword(Set<String> keyword) {
            catalog.keyword = keyword;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_THEME)
        @JsonDeserialize(as = Set.class)
        public Builder theme(Set<String> theme) {
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
        @JsonDeserialize(as = Set.class)
        public Builder description(Set<Multilanguage> description) {
            catalog.description = description;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_IDENTIFIER)
        public Builder identifier(String identifier) {
            catalog.identifier = identifier;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_ISSUED)
        public Builder issued(Instant issued) {
            catalog.issued = issued;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_MODIFIED)
        public Builder modified(Instant modified) {
            catalog.modified = modified;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_TITLE)
        public Builder title(String title) {
            catalog.title = title;
            return this;
        }

        @JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
        @JsonDeserialize(as = Set.class)
        public Builder hasPolicy(Set<Offer> policies) {
            catalog.hasPolicy = policies;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_DISTRIBUTION)
        @JsonDeserialize(as = Set.class)
        public Builder distribution(Set<Distribution> distribution) {
            catalog.distribution = distribution;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_DATASET)
        @JsonDeserialize(as = Set.class)
        public Builder dataset(Set<Dataset> datasets) {
            catalog.dataset = datasets;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_SERVICE)
        @JsonDeserialize(as = Set.class)
        public Builder service(Set<DataService> service) {
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

        @JsonProperty("createdBy")
        public Builder createdBy(String createdBy) {
            catalog.createdBy = createdBy;
            return this;
        }

        @JsonProperty("lastModifiedBy")
        public Builder lastModifiedBy(String lastModifiedBy) {
            catalog.lastModifiedBy = lastModifiedBy;
            return this;
        }

        @JsonProperty("version")
        public Builder version(Long version) {
            catalog.version = version;
            return this;
        }

        public Catalog build() {
            if (catalog.id == null) {
                catalog.id = catalog.createNewPid();
            }
            Set<ConstraintViolation<Catalog>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(catalog);
            if (violations.isEmpty()) {
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
    
    /**
     * Create new updated instance with new values from passed Catalog parameter.<br>
     * If fields are not present in updatedCatalogData, existing values will remain
     * @param updatedCatalogData
     * @return New catalog instance with updated values
     */
    public Catalog updateInstance(Catalog updatedCatalogData) {
			return Catalog.Builder.newInstance()
					.id(this.id)
			        .version(this.version)
			        .issued(this.issued)
			        .createdBy(this.createdBy)
			        .keyword(updatedCatalogData.getKeyword() != null ? updatedCatalogData.getKeyword() : this.keyword)
			        .theme(updatedCatalogData.getTheme() != null ? updatedCatalogData.getTheme() : this.theme)
			        .conformsTo(updatedCatalogData.getConformsTo() != null ? updatedCatalogData.getConformsTo() : this.conformsTo)
			        .creator(updatedCatalogData.getCreator() != null ? updatedCatalogData.getCreator() : this.creator)
			        .description(updatedCatalogData.getDescription() != null ? updatedCatalogData.getDescription() : this.description)
			        .identifier(updatedCatalogData.getIdentifier() != null ? updatedCatalogData.getIdentifier() : this.identifier)
			        .title(updatedCatalogData.getTitle() != null ? updatedCatalogData.getTitle() : this.title)
			        .distribution(updatedCatalogData.getDistribution() != null ? updatedCatalogData.getDistribution() : this.distribution)
			        .hasPolicy(updatedCatalogData.getHasPolicy() != null ? updatedCatalogData.getHasPolicy() : this.hasPolicy)
			        .dataset(updatedCatalogData.getDataset() != null ? updatedCatalogData.getDataset() : this.dataset)
			        .service(updatedCatalogData.getService() != null ? updatedCatalogData.getService() : this.service)
			        .participantId(updatedCatalogData.getParticipantId() != null ? updatedCatalogData.getParticipantId() : this.participantId)
			        .creator(updatedCatalogData.getCreator() != null ? updatedCatalogData.getCreator() : this.creator)
			        .homepage(updatedCatalogData.getHomepage() != null ? updatedCatalogData.getHomepage() : this.homepage)
			        .build();
    }
}
