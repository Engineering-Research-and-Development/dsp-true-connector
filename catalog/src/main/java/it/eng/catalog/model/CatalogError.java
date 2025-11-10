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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = CatalogError.Builder.class)
@JsonPropertyOrder(value = {DSpaceConstants.CONTEXT, DSpaceConstants.TYPE,DSpaceConstants.ID}, alphabetic = true)
public class CatalogError extends AbstractCatalogObject {

    @Serial
    private static final long serialVersionUID = -5538644369452254847L;

    private String code;
    private List<String> reason;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final CatalogError catalogError;

        private Builder() {
            catalogError = new CatalogError();
        }

        @JsonCreator
        public static CatalogError.Builder newInstance() {
            return new CatalogError.Builder();
        }

        public Builder code(String code) {
            catalogError.code = code;
            return this;
        }

        public Builder reason(List<String> reason) {
            catalogError.reason = reason;
            return this;
        }

        public CatalogError build() {
            Set<ConstraintViolation<CatalogError>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(catalogError);
            if (violations.isEmpty()) {
                return catalogError;
            }
            throw new ValidationException("CatalogError - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
    }

    @Override
    public String getType() {
        return CatalogError.class.getSimpleName();
    }
}
