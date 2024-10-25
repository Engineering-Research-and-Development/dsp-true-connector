package it.eng.negotiation.model;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = OfferResponse.Builder.class)
public class OfferResponse {
	
	private String fileId;
	private String format;

	@JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final OfferResponse offerResponse;

        private Builder() {
        	offerResponse = new OfferResponse();
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }
        
        @JsonProperty("fileId")
        public Builder fileId(String fileId) {
        	offerResponse.fileId = fileId;
            return this;
        }
        @JsonProperty("format")
        public Builder format(String format) {
        	offerResponse.format = format;
            return this;
        }
        
        public OfferResponse build() {
            Set<ConstraintViolation<OfferResponse>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(offerResponse);
            if (violations.isEmpty()) {
                return offerResponse;
            }
            throw new ValidationException(
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
        }
	}
}
