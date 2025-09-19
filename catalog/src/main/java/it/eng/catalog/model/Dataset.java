package it.eng.catalog.model;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
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
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = Dataset.Builder.class)
@JsonPropertyOrder(value = {DSpaceConstants.CONTEXT, DSpaceConstants.ID, DSpaceConstants.TYPE, DSpaceConstants.TITLE,
        DSpaceConstants.DESCRIPTION, DSpaceConstants.KEYWORD, DSpaceConstants.HAS_POLICY,
        DSpaceConstants.DISTRIBUTION}, alphabetic = true)
@Document(collection = "datasets")
public class Dataset extends AbstractCatalogObject {

    @Serial
    private static final long serialVersionUID = 8927419074799593178L;

    @Id
    @JsonProperty(DSpaceConstants.ID)
    private String id;

    // Resource
    private Set<String> keyword;
    private Set<String> theme;
    private String conformsTo;
    private String creator;
    private Set<Multilanguage> description;
    private String identifier;
    @CreatedDate
    private Instant issued;
    @LastModifiedDate
    private Instant modified;
    private String title;
    @NotNull
    private Set<Offer> hasPolicy;
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

        @JsonDeserialize(as = Set.class)
        public Builder keyword(Set<String> keyword) {
            dataset.keyword = keyword;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        public Builder theme(Set<String> theme) {
            dataset.theme = theme;
            return this;
        }

        public Builder conformsTo(String conformsTo) {
            dataset.conformsTo = conformsTo;
            return this;
        }

        public Builder creator(String creator) {
            dataset.creator = creator;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        public Builder description(Set<Multilanguage> description) {
            dataset.description = description;
            return this;
        }

        public Builder identifier(String identifier) {
            dataset.identifier = identifier;
            return this;
        }

        public Builder issued(Instant issued) {
            dataset.issued = issued;
            return this;
        }

        public Builder modified(Instant modified) {
            dataset.modified = modified;
            return this;
        }

        public Builder title(String title) {
            dataset.title = title;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        public Builder hasPolicy(Set<Offer> policies) {
            dataset.hasPolicy = policies;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        public Builder distribution(Set<Distribution> distribution) {
            dataset.distribution = distribution;
            return this;
        }

        public Builder artifact(Artifact artifact) {
            dataset.artifact = artifact;
            return this;
        }

        public Builder createdBy(String createdBy) {
            dataset.createdBy = createdBy;
            return this;
        }

        public Builder lastModifiedBy(String lastModifiedBy) {
            dataset.lastModifiedBy = lastModifiedBy;
            return this;
        }

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
        return Dataset.class.getSimpleName();
    }

    /**
     * Create new updated instance with new values from passed Dataset parameter.<br>
     * If fields are not present in updatedDataset, existing values will remain
     *
     * @param updatedDataset the dataset with updated values
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

    public void validateProtocol() {
        // Validate hasPolicy - Offer collection
        if (this.getHasPolicy() == null || this.getHasPolicy().isEmpty()) {
            throw new ValidationException("Dataset must have at least one Offer");
        }

        // Check if there's at least one non-null Offer
        if (this.getHasPolicy().stream().noneMatch(Objects::nonNull)) {
            throw new ValidationException("Dataset must have at least one non-null Offer");
        }

        // Validate each Offer in hasPolicy
        for (Offer offer : this.getHasPolicy()) {
            try {
                offer.validateProtocol();
            } catch (ValidationException e) {
                throw new ValidationException("Invalid Offer in Dataset: " + e.getMessage());
            }
        }

        // Validate Distribution collection
        if (this.getDistribution() == null || this.getDistribution().isEmpty()) {
            throw new ValidationException("Dataset must have at least one Distribution");
        }

        // Check if there's at least one non-null Distribution
        if (this.getDistribution().stream().noneMatch(Objects::nonNull)) {
            throw new ValidationException("Dataset must have at least one non-null Distribution");
        }

        // Validate Distribution collection if it exists
        for (Distribution distribution : this.getDistribution()) {
            if (distribution != null) {
                try {
                    distribution.validateProtocol();
                } catch (ValidationException e) {
                    throw new ValidationException("Invalid Distribution in Dataset: " + e.getMessage());
                }
            }
        }
    }
}
