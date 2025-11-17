package it.eng.catalog.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = CatalogRequestMessage.Builder.class)
public class CatalogRequestMessage extends AbstractCatalogObject {

    @Serial
    private static final long serialVersionUID = 8564526286689300458L;

    private List<String> filter;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final CatalogRequestMessage message;

        private Builder() {
            message = new CatalogRequestMessage();
        }

        @JsonCreator
        public static CatalogRequestMessage.Builder newInstance() {
            return new CatalogRequestMessage.Builder();
        }

        public Builder filter(List<String> filter) {
            message.filter = filter;
            return this;
        }

        public CatalogRequestMessage build() {
            Set<ConstraintViolation<CatalogRequestMessage>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(message);
            if (violations.isEmpty()) {
                return message;
            }
            throw new ValidationException("CatalogRequestMessage - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }

    @Override
    public String getType() {
        return CatalogRequestMessage.class.getSimpleName();
    }
}
