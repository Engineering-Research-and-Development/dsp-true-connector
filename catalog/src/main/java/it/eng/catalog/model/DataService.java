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
@JsonDeserialize(builder = DataService.Builder.class)
@JsonPropertyOrder(value = {DSpaceConstants.TYPE,DSpaceConstants.ID}, alphabetic = true)
@Document(collection = "dataservices")
public class DataService implements Serializable {

    @Serial
    private static final long serialVersionUID = -7490596351222880611L;

    @JsonProperty(DSpaceConstants.ID)
    @Id
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

    private String endpointDescription;
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
        private final DataService service;

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

        public Builder keyword(Set<String> keyword) {
            service.keyword = keyword;
            return this;
        }

        public Builder theme(Set<String> theme) {
            service.theme = theme;
            return this;
        }

        public Builder conformsTo(String conformsTo) {
            service.conformsTo = conformsTo;
            return this;
        }

        public Builder creator(String creator) {
            service.creator = creator;
            return this;
        }

        public Builder description(Set<Multilanguage> description) {
            service.description = description;
            return this;
        }

        public Builder identifier(String identifier) {
            service.identifier = identifier;
            return this;
        }

        public Builder issued(Instant issued) {
            service.issued = issued;
            return this;
        }

        public Builder modified(Instant modified) {
            service.modified = modified;
            return this;
        }

        public Builder title(String title) {
            service.title = title;
            return this;
        }

        public Builder endpointDescription(String endpointDescription) {
            service.endpointDescription = endpointDescription;
            return this;
        }

        public Builder endpointURL(String endpointURL) {
            service.endpointURL = endpointURL;
            return this;
        }

        public Builder createdBy(String createdBy) {
            service.createdBy = createdBy;
            return this;
        }

        public Builder lastModifiedBy(String lastModifiedBy) {
            service.lastModifiedBy = lastModifiedBy;
            return this;
        }

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
        return DataService.class.getSimpleName();
    }

    /**
     * Create new updated instance with new values from passed DataService parameter.<br>
     * If fields are not present in updatedDataService, existing values will remain
     *
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

    public void validateProtocol() {
        if (this.endpointURL == null) {
            throw new ValidationException("DataService not valid according to protocol");
        }
    }
}
