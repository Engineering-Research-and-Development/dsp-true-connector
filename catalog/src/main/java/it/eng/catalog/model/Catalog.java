package it.eng.catalog.model;

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
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;


@Getter
@JsonDeserialize(builder = Catalog.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {DSpaceConstants.CONTEXT, DSpaceConstants.ID, DSpaceConstants.TYPE, DSpaceConstants.DCT_TITLE,
        DSpaceConstants.DCT_DESCRIPTION, DSpaceConstants.DSPACE_PARTICIPANT_ID, DSpaceConstants.DCAT_KEYWORD,
        DSpaceConstants.DCAT_SERVICE, DSpaceConstants.DCAT_DATASET}, alphabetic = true)
@Document(collection = "catalogs")
public class Catalog extends AbstractCatalogObject {

    @JsonProperty(DSpaceConstants.ID)
    @Id
    private String id;

    // from Dataset
    // Resource
    @JsonProperty(DSpaceConstants.DCAT_KEYWORD)
    private Set<String> keyword;
    @JsonProperty(DSpaceConstants.DCAT_THEME)
    private Collection<String> theme;
    @JsonProperty(DSpaceConstants.DCT_CONFORMSTO)
    private String conformsTo;
    @JsonProperty(DSpaceConstants.DCT_CREATOR)
    private String creator;
    @JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
    private Collection<Multilanguage> description;
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
    private Collection<Distribution> distribution;
    // assumption - policy for allowing catalog usage/display - not mandatory for catalog
    @JsonProperty(DSpaceConstants.ODRL_HAS_POLICY)
    private Collection<Offer> hasPolicy;
    // end Dataset definition

    @JsonProperty(DSpaceConstants.DCAT_DATASET)
    @DBRef
    private Collection<Dataset> dataset;
    @JsonProperty(DSpaceConstants.DCAT_SERVICE)
    @DBRef
    private Collection<DataService> service;

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
        public Builder theme(Collection<String> theme) {
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
        public Builder description(Collection<Multilanguage> description) {
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
        public Builder hasPolicy(Collection<Offer> policies) {
            catalog.hasPolicy = policies;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_DISTRIBUTION)
        @JsonDeserialize(as = Set.class)
        public Builder distribution(Collection<Distribution> distribution) {
            catalog.distribution = distribution;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_DATASET)
        @JsonDeserialize(as = Set.class)
        public Builder dataset(Collection<Dataset> datasets) {
            catalog.dataset = datasets;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_SERVICE)
        @JsonDeserialize(as = Set.class)
        public Builder service(Collection<DataService> service) {
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
                catalog.id = catalog.createNewId();
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
}
