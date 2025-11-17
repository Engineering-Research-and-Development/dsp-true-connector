package it.eng.catalog.model;

import com.fasterxml.jackson.annotation.*;
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
@JsonDeserialize(builder = Catalog.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Document(collection = "catalogs")
public class Catalog extends AbstractCatalogObject {

    @Serial
    private static final long serialVersionUID = -7550855731500209188L;

    @JsonProperty(DSpaceConstants.ID)
    @Id
    private String id;

    // from Dataset
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

    // from Dataset definition
    @DBRef
    private Set<Distribution> distribution;
    // assumption - policy for allowing catalog usage/display - not mandatory for catalog
    private Set<Offer> hasPolicy;
    // end Dataset definition

    @DBRef
    private Set<Dataset> dataset;
    @DBRef
    @JsonIdentityReference(alwaysAsId = false)
    private Set<DataService> service;

    private String participantId;

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
        private final Catalog catalog;

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

        @JsonDeserialize(as = Set.class)
        public Builder keyword(Set<String> keyword) {
            catalog.keyword = keyword;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        public Builder theme(Set<String> theme) {
            catalog.theme = theme;
            return this;
        }

        public Builder conformsTo(String conformsTo) {
            catalog.conformsTo = conformsTo;
            return this;
        }

        public Builder creator(String creator) {
            catalog.creator = creator;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        public Builder description(Set<Multilanguage> description) {
            catalog.description = description;
            return this;
        }

        public Builder identifier(String identifier) {
            catalog.identifier = identifier;
            return this;
        }

        public Builder issued(Instant issued) {
            catalog.issued = issued;
            return this;
        }

        public Builder modified(Instant modified) {
            catalog.modified = modified;
            return this;
        }

        public Builder title(String title) {
            catalog.title = title;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        public Builder hasPolicy(Set<Offer> policies) {
            catalog.hasPolicy = policies;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        public Builder distribution(Set<Distribution> distribution) {
            catalog.distribution = distribution;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        public Builder dataset(Set<Dataset> datasets) {
            catalog.dataset = datasets;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        public Builder service(Set<DataService> service) {
            catalog.service = service;
            return this;
        }

        public Builder participantId(String participantId) {
            catalog.participantId = participantId;
            return this;
        }

        public Builder createdBy(String createdBy) {
            catalog.createdBy = createdBy;
            return this;
        }

        public Builder lastModifiedBy(String lastModifiedBy) {
            catalog.lastModifiedBy = lastModifiedBy;
            return this;
        }

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
        return Catalog.class.getSimpleName();
    }

    /**
     * Create new updated instance with new values from passed Catalog parameter.<br>
     * If fields are not present in updatedCatalogData, existing values will remain
     *
     * @param updatedCatalogData catalog for updating with new values
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
                .build();
    }

    public void validateProtocol() {
        validateDatasets();
        validateDistribution();
        validateDataService();
    }

    private void validateDatasets() {
        // Validate Dataset collection
        if (this.getDataset() == null || this.getDataset().isEmpty()) {
            throw new ValidationException("Catalog must have at least one Dataset");
        }
        // Check if there's at least one non-null dataset
        if (this.getDataset().stream().noneMatch(Objects::nonNull)) {
            throw new ValidationException("Catalog must have at least one non-null Dataset");
        }

        // Validate each Dataset in Catalog
        for (Dataset dataset : this.getDataset()) {
            try {
                dataset.validateProtocol();
            } catch (ValidationException e) {
                throw new ValidationException("Invalid Dataset in Catalog: " + e.getMessage());
            }
        }
    }

    private void validateDistribution() {
        // Validate Distribution collection
        if (this.getDistribution() == null || this.getDistribution().isEmpty()) {
            throw new ValidationException("Catalog must have at least one Distribution");
        }
        // Check if there's at least one non-null Distribution
        if (this.getDistribution().stream().noneMatch(Objects::nonNull)) {
            throw new ValidationException("Catalog must have at least one non-null Distribution");
        }

        // Validate each Distribution in Catalog
        for (Distribution distribution : this.getDistribution()) {
            try {
                distribution.validateProtocol();
            } catch (ValidationException e) {
                throw new ValidationException("Invalid Distribution in Catalog: " + e.getMessage());
            }
        }
    }

    private void validateDataService() {
        // Validate DataService collection
        if (this.getService() == null || this.getService().isEmpty()) {
            throw new ValidationException("Catalog must have at least one DataService");
        }
        // Check if there's at least one non-null DataService
        if (this.getService().stream().noneMatch(Objects::nonNull)) {
            throw new ValidationException("Catalog must have at least one non-null DataService");
        }

        // Validate each DataService in Catalog
        for (DataService dataService : this.getService()) {
            try {
                dataService.validateProtocol();
            } catch (ValidationException e) {
                throw new ValidationException("Invalid DataService in Catalog: " + e.getMessage());
            }
        }
    }
}
