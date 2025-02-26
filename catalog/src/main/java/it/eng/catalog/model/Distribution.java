
package it.eng.catalog.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
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
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

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
@JsonDeserialize(builder = Distribution.Builder.class)
@JsonPropertyOrder(value = {DSpaceConstants.TYPE, DSpaceConstants.DCT_FORMAT, DSpaceConstants.DCAT_ACCESS_SERVICE}
        , alphabetic = true)
@Document(collection = "distributions")
public class Distribution implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
    @JsonProperty(DSpaceConstants.ID)
    private String id;

    @JsonProperty(DSpaceConstants.DCT_TITLE)
    private String title;
    @JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
    private Set<Multilanguage> description;
    @JsonProperty(DSpaceConstants.DCT_ISSUED)
    @CreatedDate
    private Instant issued;
    @JsonProperty(DSpaceConstants.DCT_MODIFIED)
    @LastModifiedDate
    private Instant modified;
    
    @JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
    private Set<Offer> hasPolicy;

	@JsonProperty(DSpaceConstants.DCT_FORMAT)
	private Reference format;

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
    @JsonProperty(DSpaceConstants.DCAT_ACCESS_SERVICE)
    private Set<DataService> accessService;

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

        @JsonProperty(DSpaceConstants.DCT_TITLE)
        public Builder title(String title) {
            distribution.title = title;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
        @JsonDeserialize(as = Set.class)
        public Builder description(Set<Multilanguage> description) {
            distribution.description = description;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_ISSUED)
        public Builder issued(Instant issued) {
            distribution.issued = issued;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_MODIFIED)
        public Builder modified(Instant modified) {
            distribution.modified = modified;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_ACCESS_SERVICE)
        @JsonDeserialize(as = Set.class)
        public Builder accessService(Set<DataService> dataService) {
            distribution.accessService = dataService;
            return this;
        }

		@JsonProperty(DSpaceConstants.DCT_FORMAT)
		@JsonDeserialize(as = Reference.class)
		public Builder format(Reference format) {
			distribution.format = format;
			return this;
		}

        @JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
        @JsonDeserialize(as = Set.class)
        @JsonSerialize(as = Set.class)
        public Builder hasPolicy(Set<Offer> hasPolicy) {
            distribution.hasPolicy = hasPolicy;
            return this;
        }

        @JsonProperty("createdBy")
        public Distribution.Builder createdBy(String createdBy) {
            distribution.createdBy = createdBy;
            return this;
        }

        @JsonProperty("lastModifiedBy")
        public Distribution.Builder lastModifiedBy(String lastModifiedBy) {
            distribution.lastModifiedBy = lastModifiedBy;
            return this;
        }

        @JsonProperty("version")
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
        return DSpaceConstants.DSPACE + Distribution.class.getSimpleName();
    }
    
    /**
     * Create new updated instance with new values from passed Distribution parameter.<br>
     * If fields are not present in updatedDistribution, existing values will remain
     * @param updatedDistribution
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
}
