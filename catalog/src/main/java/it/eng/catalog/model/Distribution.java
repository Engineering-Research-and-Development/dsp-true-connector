package it.eng.catalog.model;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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
import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = Distribution.Builder.class)
@JsonPropertyOrder(value = {DSpaceConstants.TYPE, DSpaceConstants.ID, DSpaceConstants.FORMAT, DSpaceConstants.ACCESS_SERVICE}, alphabetic = true)
@Document(collection = "distributions")
public class Distribution implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @JsonProperty(DSpaceConstants.ID)
    private String id;

    private String title;
    private Set<Multilanguage> description;
    @CreatedDate
    private Instant issued;
    @LastModifiedDate
    private Instant modified;

    private Set<Offer> hasPolicy;

    private String format;

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

    @NotNull
    @DBRef
    private DataService accessService;

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

        @JsonProperty(DSpaceConstants.ID)
        public Builder id(String id) {
            distribution.id = id;
            return this;
        }

        public Builder title(String title) {
            distribution.title = title;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        public Builder description(Set<Multilanguage> description) {
            distribution.description = description;
            return this;
        }

        public Builder issued(Instant issued) {
            distribution.issued = issued;
            return this;
        }

        public Builder modified(Instant modified) {
            distribution.modified = modified;
            return this;
        }

        public Builder accessService(DataService dataService) {
            distribution.accessService = dataService;
            return this;
        }

        public Builder format(String format) {
            distribution.format = format;
            return this;
        }

        @JsonDeserialize(as = Set.class)
        @JsonSerialize(as = Set.class)
        public Builder hasPolicy(Set<Offer> hasPolicy) {
            distribution.hasPolicy = hasPolicy;
            return this;
        }

        public Distribution.Builder createdBy(String createdBy) {
            distribution.createdBy = createdBy;
            return this;
        }

        public Distribution.Builder lastModifiedBy(String lastModifiedBy) {
            distribution.lastModifiedBy = lastModifiedBy;
            return this;
        }

        public Distribution.Builder version(Long version) {
            distribution.version = version;
            return this;
        }

        public Distribution build() {
            if (distribution.id == null) {
                distribution.id = "urn:uuid:" + UUID.randomUUID().toString();
            }
            Set<ConstraintViolation<Distribution>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(distribution);
            if (violations.isEmpty()) {
                return distribution;
            }
            throw new ValidationException("Distribution - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }

    @JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
    public String getType() {
        return Distribution.class.getSimpleName();
    }

    /**
     * Create new updated instance with new values from passed Distribution parameter.<br>
     * If fields are not present in updatedDistribution, existing values will remain
     *
     * @param updatedDistribution distribution with new values
     * @return new updated distribution instance
     */
    public Distribution updateInstance(Distribution updatedDistribution) {
        return Distribution.Builder.newInstance()
                .id(this.id)
                .version(this.version)
                .issued(this.issued)
                .createdBy(this.createdBy)
                .modified(updatedDistribution.getModified() != null ? updatedDistribution.getModified() : this.modified)
                .title(updatedDistribution.getTitle() != null ? updatedDistribution.getTitle() : this.title)
                .description(updatedDistribution.getDescription() != null ? updatedDistribution.getDescription() : this.description)
                .accessService(updatedDistribution.getAccessService() != null ? updatedDistribution.getAccessService() : this.accessService)
                .hasPolicy(updatedDistribution.getHasPolicy() != null ? updatedDistribution.getHasPolicy() : this.hasPolicy)
                .format(updatedDistribution.getFormat() != null ? updatedDistribution.getFormat() : this.format)
                .build();

    }

    public void validateProtocol() {
        // Validate AccessService collection
        if (this.getAccessService() == null) {
            throw new ValidationException("Distribution must have AccessService");
        }

        // Validate AccessService
        DataService dataService = this.getAccessService();
        try {
            dataService.validateProtocol();
        } catch (ValidationException e) {
            throw new ValidationException("Invalid AccessService in Distribution: " + e.getMessage());
        }
    }
}
