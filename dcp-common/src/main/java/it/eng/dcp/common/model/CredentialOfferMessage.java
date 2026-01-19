package it.eng.dcp.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@JsonDeserialize(builder = CredentialOfferMessage.Builder.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder({DCPConstants.CONTEXT, "type", "issuer", "credentials"})
public class CredentialOfferMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty(value = DCPConstants.CONTEXT, access = JsonProperty.Access.READ_ONLY)
    private List<String> context = List.of(DCPConstants.DCP_CONTEXT);

    @JsonProperty(value = DCPConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    private String type;

    @NotNull
    private String issuer;

    @NotNull
    @Size(min = 1)
    @JsonProperty(value = "credentials")
    private List<CredentialObject> credentialObjects = new ArrayList<>();

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final CredentialOfferMessage msg;

        private Builder() {
            msg = new CredentialOfferMessage();
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder issuer(String issuer) {
            msg.issuer = issuer;
            return this;
        }

        @JsonProperty("credentials")
        public Builder offeredCredentials(List<CredentialObject> credentialObjects) {
            if (credentialObjects != null) {
                msg.credentialObjects.clear();
                msg.credentialObjects.addAll(credentialObjects);
            }
            return this;
        }

        public CredentialOfferMessage build() {
            try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
                Set<ConstraintViolation<CredentialOfferMessage>> violations = vf.getValidator().validate(msg);
                if (violations.isEmpty()) {
                    if (msg.credentialObjects == null) {
                        msg.credentialObjects = new ArrayList<>();
                    }
                    return msg;
                }
                throw new ValidationException("CredentialOfferMessage - " +
                        violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(",")));
            }
        }
    }

    public String getType() {
        return CredentialOfferMessage.class.getSimpleName();
    }

    @JsonDeserialize(builder = CredentialObject.Builder.class)
    @Getter
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @JsonPropertyOrder({"id", "type", "credentialType", "offerReason", "bindingMethods", "profile", "issuancePolicy", "credentialSchema"})
    public static class CredentialObject implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty(value = "id", required = true)
        private String id;

//        @NotNull - need to support id's only
        private String credentialType;

        private Map<String, Object> issuancePolicy;

        @JsonProperty(value = "bindingMethods")
        private final List<String> bindingMethods = new ArrayList<>();

        @JsonProperty(value = "profile")
        private String profile;

        @JsonProperty(value = "type")
        private String type;

        @JsonProperty(value = "offerReason")
        private String offerReason;

        @JsonProperty(value = "credentialSchema")
        private String credentialSchema;

        public String getType() {
            return CredentialObject.class.getSimpleName();
        }

        @JsonPOJOBuilder(withPrefix = "")
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Builder {
            private final CredentialObject offered;

            private Builder() {
                offered = new CredentialObject();
            }

            public static Builder newInstance() {
                return new Builder();
            }

            public Builder id(String id) {
                offered.id = id;
                return this;
            }

            public Builder credentialType(String credentialType) {
                offered.credentialType = credentialType;
                return this;
            }

            public Builder issuancePolicy(Map<String, Object> issuancePolicy) {
                offered.issuancePolicy = issuancePolicy;
                return this;
            }

            public Builder bindingMethods(List<String> bindingMethods) {
                if (bindingMethods != null) {
                    offered.bindingMethods.clear();
                    offered.bindingMethods.addAll(bindingMethods);
                }
                return this;
            }

            public Builder profile(String profile) {
                offered.profile = profile;
                return this;
            }

            public Builder offerReason(String offerReason) {
                offered.offerReason = offerReason;
                return this;
            }

            public Builder credentialSchema(String credentialSchema) {
                offered.credentialSchema = credentialSchema;
                return this;
            }

            public CredentialObject build() {
                try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
                    Set<ConstraintViolation<CredentialObject>> violations = vf.getValidator().validate(offered);
                    if (violations.isEmpty()) {
                        return offered;
                    }
                    throw new ValidationException("OfferedCredential - " +
                            violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(",")));
                }
            }
        }
    }
}
