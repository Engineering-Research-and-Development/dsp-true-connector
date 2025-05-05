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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.Artifact;
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
@JsonPropertyOrder(value = {DSpaceConstants.CONTEXT, DSpaceConstants.ID, DSpaceConstants.TYPE, DSpaceConstants.DCT_TITLE,
        DSpaceConstants.DCT_DESCRIPTION, DSpaceConstants.DCAT_KEYWORD, DSpaceConstants.ODRL_HAS_POLICY,
        DSpaceConstants.DCAT_DISTRIBUTION}, alphabetic = true)
@Document(collection = "datasets")
public class Dataset extends AbstractCatalogObject {

	private static final long serialVersionUID = 8927419074799593178L;

	@Id
    @JsonProperty(DSpaceConstants.ID)
    private String id;

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
    @NotNull
    @JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
    private Set<Offer> hasPolicy;
    @JsonProperty(DSpaceConstants.DCAT_DISTRIBUTION)
    @DBRef
    private Set<Distribution> distribution;
    
    @JsonIgnore
    @DBRef
    private Artifact artifact;
    
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
        @JsonDeserialize(as = Set.class)
        public Builder keyword(Set<String> keyword) {
            dataset.keyword = keyword;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_THEME)
        @JsonDeserialize(as = Set.class)
        public Builder theme(Set<String> theme) {
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
        @JsonDeserialize(as = Set.class)
        public Builder description(Set<Multilanguage> description) {
            dataset.description = description;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_IDENTIFIER)
        public Builder identifier(String identifier) {
            dataset.identifier = identifier;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_ISSUED)
        public Builder issued(Instant issued) {
            dataset.issued = issued;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_MODIFIED)
        public Builder modified(Instant modified) {
            dataset.modified = modified;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_TITLE)
        public Builder title(String title) {
            dataset.title = title;
            return this;
        }

        @JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
        @JsonDeserialize(as = Set.class)
        public Builder hasPolicy(Set<Offer> policies) {
            dataset.hasPolicy = policies;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_DISTRIBUTION)
        @JsonDeserialize(as = Set.class)
        public Builder distribution(Set<Distribution> distribution) {
            dataset.distribution = distribution;
            return this;
        }

        @JsonProperty("artifact")
        public Builder artifact(Artifact artifact) {
            dataset.artifact = artifact;
            return this;
        }
        
        @JsonProperty("createdBy")
        public Builder createdBy(String createdBy) {
            dataset.createdBy = createdBy;
            return this;
        }

        @JsonProperty("lastModifiedBy")
        public Builder lastModifiedBy(String lastModifiedBy) {
            dataset.lastModifiedBy = lastModifiedBy;
            return this;
        }

        @JsonProperty("version")
        public Builder version(Long version) {
            dataset.version = version;
            return this;
        }

        public Dataset build() {
            if (dataset.id == null) {
                dataset.id = dataset.createNewPid();
            }
            Set<ConstraintViolation<Dataset>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(dataset);
            if (violations.isEmpty()) {
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
    
    /**
     * Create new updated instance with new values from passed Dataset parameter.<br>
     * If fields are not present in updatedDataset, existing values will remain
     * @param updatedDataset
     * @return new updated dataset instance
     */
    public Dataset updateInstance(Dataset updatedDataset) {
		return Dataset.Builder.newInstance()
         .id(this.id)
         .version(this.version)
         .issued(this.issued)
         .createdBy(this.createdBy)
         .keyword(updatedDataset.getKeyword() != null ? updatedDataset.getKeyword() : this.keyword)
         .theme(updatedDataset.getTheme() != null ? updatedDataset.getTheme() : this.theme)
         .conformsTo(updatedDataset.getConformsTo() != null ? updatedDataset.getConformsTo() : this.conformsTo)
         .creator(updatedDataset.getCreator() != null ? updatedDataset.getCreator() : this.creator)
         .description(updatedDataset.getDescription() != null ? updatedDataset.getDescription() : this.description)
         .identifier(updatedDataset.getIdentifier() != null ? updatedDataset.getIdentifier() : this.identifier)
         .title(updatedDataset.getTitle() != null ? updatedDataset.getTitle() : this.title)
         .distribution(updatedDataset.getDistribution() != null ? updatedDataset.getDistribution() : this.distribution)
         .hasPolicy(updatedDataset.getHasPolicy() != null ? updatedDataset.getHasPolicy() : this.hasPolicy)
         .artifact(updatedDataset.getArtifact() != null ? updatedDataset.getArtifact() : this.artifact)
         .build();
  }
}
