package it.eng.dcp.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@JsonDeserialize(builder = DidDocument.Builder.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder(value = {DSpaceConstants.CONTEXT, DSpaceConstants.ID, "it/eng/dcp/service", "verificationMethod"}, alphabetic = true)
public class DidDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty(value = DSpaceConstants.CONTEXT, access = JsonProperty.Access.READ_ONLY)
    private List<String> context = List.of(DSpaceConstants.DID_CONTEXT, DSpaceConstants.DCP_NAMESPACE);

    @NotNull
    private String id;

    @JsonProperty("service")
    private List<ServiceEntry> services = new ArrayList<>();

    @JsonProperty("verificationMethod")
    private List<VerificationMethod> verificationMethods = new ArrayList<>();

//    This is only one type of verification relationship thus it will be modeled as a list of strings, see below
//    private List<String> capabilityInvocation;
//    TODO: to be implemented in future releases (currently not used by DCP TCK)
//    private List<VerificationRelationships> verificationRelationships;


    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final DidDocument document;

        private Builder() {
            document = new DidDocument();
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            document.id = id;
            return this;
        }

        @JsonProperty("service")
        public Builder service(List<ServiceEntry> services) {
            document.services.addAll(services);
            return this;
        }

        @JsonProperty("verificationMethod")
        public Builder verificationMethod(List<VerificationMethod> methods) {
            document.verificationMethods.addAll(methods);
            return this;
        }

//        public Builder verificationRelationships(List<VerificationRelationships> verificationRelationships) {
//            document.verificationRelationships = verificationRelationships;
//            return this;
//        }

        public DidDocument build() {
            try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
                Set<ConstraintViolation<DidDocument>> violations = vf.getValidator().validate(document);
                if (violations.isEmpty()) {
                    return document;
                }
                throw new ValidationException("DidDocument - " +
                        violations.stream()
                                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                                .collect(Collectors.joining(",")));
            }
        }
    }
}
