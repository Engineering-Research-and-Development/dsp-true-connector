package it.eng.catalog.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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

import java.io.Serial;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = DatasetRequestMessage.Builder.class)
@JsonPropertyOrder(value = {DSpaceConstants.CONTEXT, DSpaceConstants.TYPE,DSpaceConstants.ID}, alphabetic = true)
public class DatasetRequestMessage extends AbstractCatalogObject {

    @Serial
    private static final long serialVersionUID = 8681130342830944384L;

    @NotNull
    private String dataset;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final DatasetRequestMessage datasetRequestMessage;

        private Builder() {
            datasetRequestMessage = new DatasetRequestMessage();
        }

        @JsonCreator
        public static DatasetRequestMessage.Builder newInstance() {
            return new DatasetRequestMessage.Builder();
        }

        public Builder dataset(String dataset) {
            datasetRequestMessage.dataset = dataset;
            return this;
        }

        public DatasetRequestMessage build() {
            Set<ConstraintViolation<DatasetRequestMessage>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(datasetRequestMessage);
            if (violations.isEmpty()) {
                return datasetRequestMessage;
            }
            throw new ValidationException("DatasetRequestMessage - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }

    @Override
    public String getType() {
        return DatasetRequestMessage.class.getSimpleName();
    }

}
