package it.eng.dcp.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonDeserialize(builder = CredentialOfferMessage.Builder.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CredentialOfferMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty(value = DSpaceConstants.CONTEXT)
    private List<String> context = new ArrayList<>();

    @NotNull
    private String type;

    @NotNull
    @Size(min = 1)
    private List<OfferedCredential> offeredCredentials = new ArrayList<>();

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final CredentialOfferMessage msg;

        private Builder() {
            msg = new CredentialOfferMessage();
            msg.context.add(DSpaceConstants.DCP_CONTEXT);
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty(DSpaceConstants.CONTEXT)
        public Builder context(List<String> context) {
            if (context != null) {
                msg.context.clear();
                msg.context.addAll(context);
            }
            return this;
        }

        public Builder type(String type) {
            msg.type = type;
            return this;
        }

        @JsonProperty("offeredCredentials")
        public Builder offeredCredentials(List<OfferedCredential> offeredCredentials) {
            if (offeredCredentials != null) {
                msg.offeredCredentials.clear();
                msg.offeredCredentials.addAll(offeredCredentials);
            }
            return this;
        }

        public CredentialOfferMessage build() {
            try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
                Set<ConstraintViolation<CredentialOfferMessage>> violations = vf.getValidator().validate(msg);
                if (violations.isEmpty()) {
                    if (msg.offeredCredentials == null) {
                        msg.offeredCredentials = new ArrayList<>();
                    }
                    return msg;
                }
                throw new ValidationException("CredentialOfferMessage - " +
                        violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(",")));
            }
        }
    }

    @JsonDeserialize(builder = OfferedCredential.Builder.class)
    @Getter
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OfferedCredential implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @NotNull
        private String credentialType;

        @NotNull
        private String format;

        private Map<String, Object> issuancePolicy;

        @JsonPOJOBuilder(withPrefix = "")
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Builder {
            private final OfferedCredential offered;

            private Builder() {
                offered = new OfferedCredential();
            }

            public static Builder newInstance() {
                return new Builder();
            }

            public Builder credentialType(String credentialType) {
                offered.credentialType = credentialType;
                return this;
            }

            public Builder format(String format) {
                offered.format = format;
                return this;
            }

            public Builder issuancePolicy(Map<String, Object> issuancePolicy) {
                offered.issuancePolicy = issuancePolicy;
                return this;
            }

            public OfferedCredential build() {
                try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
                    Set<ConstraintViolation<OfferedCredential>> violations = vf.getValidator().validate(offered);
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

