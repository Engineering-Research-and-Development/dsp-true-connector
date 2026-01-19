package it.eng.dcp.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.*;
import java.util.stream.Collectors;

@JsonDeserialize(builder = IssuerMetadata.Builder.class)
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Getter
@JsonPropertyOrder({DCPConstants.CONTEXT, "type", "issuer", "credentialsSupported"})
public class IssuerMetadata extends BaseDcpMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    // public static final String MESSAGE_TYPE = "IssuerMetadata";

    @Override
    @JsonProperty(value = DCPConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return IssuerMetadata.class.getSimpleName();
    }

    @NotNull
    private String issuer;

    @NotNull
    @Size(min = 1)
    private List<CredentialObject> credentialsSupported = new ArrayList<>();

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final IssuerMetadata meta;

        private Builder() {
            meta = new IssuerMetadata();
            meta.getContext().add(DCPConstants.DCP_CONTEXT);
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty(DCPConstants.CONTEXT)
        public Builder context(List<String> context) {
            if (context != null) {
                meta.getContext().clear();
                meta.getContext().addAll(context);
            }
            return this;
        }

        public Builder issuer(String issuer) {
            meta.issuer = issuer;
            return this;
        }

        @JsonProperty("credentialsSupported")
        public Builder credentialsSupported(List<CredentialObject> credentialsSupported) {
            if (credentialsSupported != null) {
                meta.credentialsSupported.clear();
                meta.credentialsSupported.addAll(credentialsSupported);
            }
            return this;
        }

        public IssuerMetadata build() {
            try {
                meta.validateBase();
                if (meta.credentialsSupported == null) meta.credentialsSupported = new ArrayList<>();
                // Business rule: credentialsSupported must not contain duplicate ids
                Set<String> ids = new HashSet<>();
                for (CredentialObject co : meta.credentialsSupported) {
                    if (co.getId() == null) continue;
                    if (!ids.add(co.getId())) {
                        throw new ValidationException("IssuerMetadata - duplicate credential id: " + co.getId());
                    }
                }
                return meta;
            } catch (Exception e) {
                throw new ValidationException("IssuerMetadata - " + e.getMessage());
            }
        }
    }

    @JsonDeserialize(builder = CredentialObject.Builder.class)
    @Getter
    @JsonPropertyOrder({"id", "type", "credentialType", "offerReason", "credentialSchema", "bindingMethods", "profile", "issuancePolicy"})
    @NoArgsConstructor
    public static class CredentialObject implements java.io.Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @NotNull
        private String id;

        @NotNull
        private String credentialType;

        private String credentialSchema;

        private String profile;

        private Map<String, Object> issuancePolicy;

        private final List<String> bindingMethods = new ArrayList<>();

        public String getType() {
            return CredentialObject.class.getSimpleName();
        }

        @JsonPOJOBuilder(withPrefix = "")
        @JsonIgnoreProperties(ignoreUnknown = true)
        @Getter
        public static class Builder {
            private final CredentialObject obj;

            private Builder() {
                obj = new CredentialObject();
            }

            public static Builder newInstance() {
                return new Builder();
            }

            public Builder id(String id) {
                obj.id = id;
                return this;
            }

            public Builder credentialType(String credentialType) {
                obj.credentialType = credentialType;
                return this;
            }

            public Builder credentialSchema(String credentialSchema) {
                obj.credentialSchema = credentialSchema;
                return this;
            }

            public Builder profile(String profile) {
                obj.profile = profile;
                return this;
            }

            public Builder issuancePolicy(Map<String, Object> issuancePolicy) {
                obj.issuancePolicy = issuancePolicy;
                return this;
            }

            public Builder bindingMethods(List<String> bindingMethods) {
                if (bindingMethods != null) {
                    obj.bindingMethods.clear();
                    obj.bindingMethods.addAll(bindingMethods);
                }
                return this;
            }

            public CredentialObject build() {
                try (jakarta.validation.ValidatorFactory vf = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
                    java.util.Set<jakarta.validation.ConstraintViolation<CredentialObject>> violations = vf.getValidator().validate(obj);
                    if (violations.isEmpty()) {
                        return obj;
                    }
                    throw new jakarta.validation.ValidationException("CredentialObject - " +
                            violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(",")));
                }
            }
        }
    }
}
