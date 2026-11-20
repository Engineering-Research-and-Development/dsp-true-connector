package it.eng.dcp.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
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
@JsonPropertyOrder(value = {DCPConstants.CONTEXT, DCPConstants.ID, "it/eng/dcp/service", "verificationMethod"}, alphabetic = true)
public class DidDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** DID Core verification relationship name {@code authentication}. */
    public static final String AUTHENTICATION = "authentication";
    /** DID Core verification relationship name {@code capabilityInvocation}. */
    public static final String CAPABILITY_INVOCATION = "capabilityInvocation";
    /** DID Core verification relationship name {@code assertionMethod}. */
    public static final String ASSERTION_METHOD = "assertionMethod";

    @JsonProperty(value = DCPConstants.CONTEXT, access = JsonProperty.Access.READ_ONLY)
    private List<String> context = List.of(DCPConstants.DID_CONTEXT, DCPConstants.DCP_NAMESPACE);

    @NotNull
    private String id;

    @JsonProperty("service")
    private List<ServiceEntry> services = new ArrayList<>();

    @JsonProperty("verificationMethod")
    private List<VerificationMethod> verificationMethods = new ArrayList<>();

    /** Verification methods usable to authenticate as the DID subject (for example VP proofs). */
    @JsonProperty(AUTHENTICATION)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> authentication = new ArrayList<>();

    /** Verification methods usable to invoke capabilities (DCP Self-Issued ID Tokens, §4.3.3). */
    @JsonProperty(CAPABILITY_INVOCATION)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> capabilityInvocation = new ArrayList<>();

    /** Verification methods usable to issue assertions (Verifiable Credential proofs). */
    @JsonProperty(ASSERTION_METHOD)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> assertionMethod = new ArrayList<>();


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

        /**
         * Adds references to the verification methods listed under {@code authentication}.
         *
         * @param refs verification method IDs (absolute DID URLs or relative fragments); null is ignored
         * @return this builder
         */
        @JsonProperty(AUTHENTICATION)
        @JsonDeserialize(using = VerificationRelationshipDeserializer.class)
        public Builder authentication(List<String> refs) {
            if (refs != null) {
                document.authentication.addAll(refs);
            }
            return this;
        }

        /**
         * Adds references to the verification methods listed under {@code capabilityInvocation}.
         *
         * @param refs verification method IDs (absolute DID URLs or relative fragments); null is ignored
         * @return this builder
         */
        @JsonProperty(CAPABILITY_INVOCATION)
        @JsonDeserialize(using = VerificationRelationshipDeserializer.class)
        public Builder capabilityInvocation(List<String> refs) {
            if (refs != null) {
                document.capabilityInvocation.addAll(refs);
            }
            return this;
        }

        /**
         * Adds references to the verification methods listed under {@code assertionMethod}.
         *
         * @param refs verification method IDs (absolute DID URLs or relative fragments); null is ignored
         * @return this builder
         */
        @JsonProperty(ASSERTION_METHOD)
        @JsonDeserialize(using = VerificationRelationshipDeserializer.class)
        public Builder assertionMethod(List<String> refs) {
            if (refs != null) {
                document.assertionMethod.addAll(refs);
            }
            return this;
        }

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
