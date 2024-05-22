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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = DataService.Builder.class)
@JsonPropertyOrder(value = {"@id", "@type"}, alphabetic = true)
@Document(collection = "dataservices")
public class DataService {

    @JsonProperty(DSpaceConstants.ID)
    @Id
    private String id;
    // Resource
    @JsonProperty(DSpaceConstants.DCAT_KEYWORD)
    private List<String> keyword;
    @JsonProperty(DSpaceConstants.DCAT_THEME)
    private List<String> theme;
    @JsonProperty(DSpaceConstants.DCT_CONFORMSTO)
    private String conformsTo;
    @JsonProperty(DSpaceConstants.DCT_CREATOR)
    private String creator;
    @JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
    private List<Multilanguage> description;
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
    @JsonProperty(DSpaceConstants.DCAT_SERVES_DATASET)
    // @DBRef// Check if this is correct
    private List<Dataset> servesDataset;
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

        public static Builder updateInstance(DataService existingDataService, DataService updatedDataService) {

            Builder builder = newInstance();
            builder.id(existingDataService.getId());
            builder.version(existingDataService.getVersion());
            builder.issued(existingDataService.getIssued());
            builder.createdBy(existingDataService.getCreatedBy());

            builder.keyword(updatedDataService.getKeyword() != null ? updatedDataService.getKeyword() : existingDataService.getKeyword());
            builder.theme(updatedDataService.getTheme() != null ? updatedDataService.getTheme() : existingDataService.getTheme());
            builder.conformsTo(updatedDataService.getConformsTo() != null ? updatedDataService.getConformsTo() : existingDataService.getConformsTo());
            builder.creator(updatedDataService.getCreator() != null ? updatedDataService.getCreator() : existingDataService.getCreator());
            builder.description(updatedDataService.getDescription() != null ? updatedDataService.getDescription() : existingDataService.getDescription());
            builder.identifier(updatedDataService.getIdentifier() != null ? updatedDataService.getIdentifier() : existingDataService.getIdentifier());
            builder.title(updatedDataService.getTitle() != null ? updatedDataService.getTitle() : existingDataService.getTitle());
            builder.endpointDescription(updatedDataService.getEndpointDescription() != null ? updatedDataService.getEndpointDescription() : existingDataService.getEndpointDescription());
            builder.endpointURL(updatedDataService.getEndpointURL() != null ? updatedDataService.getEndpointURL() : existingDataService.getEndpointURL());
            builder.servesDataset(updatedDataService.getServesDataset() != null ? updatedDataService.getServesDataset() : existingDataService.getServesDataset());

            return builder;
        }


        @JsonProperty(DSpaceConstants.ID)
        public Builder id(String id) {
            service.id = id;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_KEYWORD)
        public Builder keyword(List<String> keyword) {
            service.keyword = keyword;
            return this;
        }

        @JsonProperty(DSpaceConstants.DCAT_THEME)
        public Builder theme(List<String> theme) {
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
        public Builder description(List<Multilanguage> description) {
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

        @JsonProperty(DSpaceConstants.DCAT_SERVES_DATASET)
        public Builder servesDataset(List<Dataset> servesDataset) {
            service.servesDataset = servesDataset;
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
                service.id = UUID.randomUUID().toString();
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
}
