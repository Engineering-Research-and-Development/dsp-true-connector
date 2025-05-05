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

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = DataService.Builder.class)
@JsonPropertyOrder(value = {"@id", "@type"}, alphabetic = true)
@Document(collection = "dataservices")
public class DataService implements Serializable {

	private static final long serialVersionUID = -7490596351222880611L;

	@JsonProperty(DSpaceConstants.ID)
    @Id
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
    @LastModifiedDate
    @JsonProperty(DSpaceConstants.DCT_MODIFIED)
    private Instant modified;
    @JsonProperty(DSpaceConstants.DCT_TITLE)
    private String title;
    
    @JsonProperty(DSpaceConstants.DCAT_ENDPOINT_DESCRIPTION)
    private String endpointDescription;
    @JsonProperty(DSpaceConstants.DCAT_ENDPOINT_URL)
    private String endpointURL;
    
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
        private DataService service;

        private Builder() {
            service = new DataService();
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty(DSpaceConstants.ID)
        public Builder id(String id) {
            service.id = id;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_KEYWORD)
        public Builder keyword(Set<String> keyword) {
            service.keyword = keyword;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_THEME)
        public Builder theme(Set<String> theme) {
            service.theme = theme;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_CONFORMSTO)
        public Builder conformsTo(String conformsTo) {
            service.conformsTo = conformsTo;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_CREATOR)
        public Builder creator(String creator) {
            service.creator = creator;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
        public Builder description(Set<Multilanguage> description) {
            service.description = description;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_IDENTIFIER)
        public Builder identifier(String identifier) {
            service.identifier = identifier;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_ISSUED)
        public Builder issued(Instant issued) {
            service.issued = issued;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_MODIFIED)
        public Builder modified(Instant modified) {
            service.modified = modified;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCT_TITLE)
        public Builder title(String title) {
            service.title = title;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_ENDPOINT_DESCRIPTION)
        public Builder endpointDescription(String endpointDescription) {
            service.endpointDescription = endpointDescription;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_ENDPOINT_URL)
        public Builder endpointURL(String endpointURL) {
            service.endpointURL = endpointURL;
            return this;
        }

        @JsonProperty("createdBy")
        public Builder createdBy(String createdBy) {
            service.createdBy = createdBy;
            return this;
        }

        @JsonProperty("lastModifiedBy")
        public Builder lastModifiedBy(String lastModifiedBy) {
            service.lastModifiedBy = lastModifiedBy;
            return this;
        }

        @JsonProperty("version")
        public Builder version(Long version) {
            service.version = version;
            return this;
        }

        public DataService build() {
            if (service.id == null) {
                service.id = "urn:uuid" + UUID.randomUUID().toString();
            }
            Set<ConstraintViolation<DataService>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(service);
            if (violations.isEmpty()) {
                return service;
            }
            throw new ValidationException("DataService - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }

    @JsonProperty(value = DSpaceConstants.TYPE, access = Access.READ_ONLY)
    public String getType() {
        return DSpaceConstants.DCAT + DataService.class.getSimpleName();
    }
    
    /**
     * Create new updated instance with new values from passed DataService parameter.<br>
     * If fields are not present in updatedDataService, existing values will remain
     * @param updatedDataService
     * @return new updated dataService instance
     */
    public DataService updateInstance(DataService updatedDataService) {
		return DataService.Builder.newInstance()
         .id(this.id)
         .version(this.version)
         .issued(this.issued)
         .createdBy(this.createdBy)
         .keyword(updatedDataService.getKeyword() != null ? updatedDataService.getKeyword() : this.keyword)
         .theme(updatedDataService.getTheme() != null ? updatedDataService.getTheme() : this.theme)
         .conformsTo(updatedDataService.getConformsTo() != null ? updatedDataService.getConformsTo() : this.conformsTo)
         .creator(updatedDataService.getCreator() != null ? updatedDataService.getCreator() : this.creator)
         .description(updatedDataService.getDescription() != null ? updatedDataService.getDescription() : this.description)
         .identifier(updatedDataService.getIdentifier() != null ? updatedDataService.getIdentifier() : this.identifier)
         .title(updatedDataService.getTitle() != null ? updatedDataService.getTitle() : this.title)
         .endpointDescription(updatedDataService.getEndpointDescription() != null ? updatedDataService.getEndpointDescription() : this.endpointDescription)
         .endpointURL(updatedDataService.getEndpointURL() != null ? updatedDataService.getEndpointURL() : this.endpointURL)
         .build();
  }
}
