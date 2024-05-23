
package it.eng.catalog.model;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
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
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = Distribution.Builder.class)
@JsonPropertyOrder(value = {DSpaceConstants.TYPE, DSpaceConstants.DCT_FORMAT, DSpaceConstants.DCAT_ACCESS_SERVICE}
        , alphabetic = true)
@Document(collection = "distributions")
public class Distribution {

    @Id
    @JsonProperty(DSpaceConstants.ID)
    private String id;

    @JsonProperty(DSpaceConstants.DCT_TITLE)
    private String title;
    @JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
    private List<Multilanguage> description;
    @JsonProperty(DSpaceConstants.DCT_ISSUED)
    @CreatedDate
    private Instant issued;
    @JsonProperty(DSpaceConstants.DCT_MODIFIED)
    @LastModifiedDate
    private Instant modified;
    @JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
    private List<Offer> hasPolicy;

//	@JsonProperty(DSpaceConstants.DCT_FORMAT)
//	private Reference format;

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
    private List<DataService> accessService;

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

        public static Builder updateInstance(Distribution existingDistribution, Distribution updatedDistribution) {

            Distribution.Builder builder = newInstance();
            builder.id(existingDistribution.getId());
            builder.version(existingDistribution.getVersion());
            builder.issued(existingDistribution.getIssued());
            builder.createdBy(existingDistribution.getCreatedBy());
            builder.modified(updatedDistribution.getModified() != null ? updatedDistribution.getModified() : existingDistribution.getModified());
            builder.title(updatedDistribution.getTitle() != null ? updatedDistribution.getTitle() : existingDistribution.getTitle());
            builder.description(updatedDistribution.getDescription() != null ? updatedDistribution.getDescription() : existingDistribution.getDescription());
            builder.accessService(updatedDistribution.getAccessService() != null ? updatedDistribution.getAccessService() : existingDistribution.getAccessService());
            builder.hasPolicy(updatedDistribution.getHasPolicy() != null ? updatedDistribution.getHasPolicy() : existingDistribution.getHasPolicy());

            return builder;
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
        public Builder description(List<Multilanguage> description) {
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
        public Builder accessService(List<DataService> dataService) {
            distribution.accessService = dataService;
            return this;
        }

//		@JsonProperty(DSpaceConstants.DCT_FORMAT)
//		public Builder format(Reference format) {
//			distribution.format = format;
//			return this;
//		}

        @JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
        public Builder hasPolicy(List<Offer> policies) {
            distribution.hasPolicy = policies;
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
                distribution.id = UUID.randomUUID().toString();
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
}
