package it.eng.dcp.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@JsonDeserialize(builder = CredentialMessage.Builder.class)
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Getter
public class CredentialMessage extends BaseDcpMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    @JsonProperty(value = DCPConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return CredentialMessage.class.getSimpleName();
    }

    @NotNull
    private String issuerPid;

    /** Holder-side request ID (DCP §6.5.1); optional. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String holderPid;

    private String status; // e.g., ISSUED or REJECTED

    /** Optional reason for a rejection (DCP §6.5.1); omitted from JSON when absent, never sent as null. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String rejectionReason;

    // New: optional requestId coming from issuer to correlate requests
    private String requestId;

    /** Issued credentials; optional, for example absent in a rejection (DCP §6.5.1 Example 9). */
    @JsonProperty(value = "credentials")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<CredentialContainer> credentials = new ArrayList<>();

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final CredentialMessage msg;

        private Builder() {
            msg = new CredentialMessage();
            msg.getContext().add(DCPConstants.DCP_CONTEXT);
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty(DCPConstants.CONTEXT)
        public Builder context(List<String> context) {
            if (context != null) {
                msg.getContext().clear();
                msg.getContext().addAll(context);
            }
            return this;
        }

        public Builder issuerPid(String issuerPid) {
            msg.issuerPid = issuerPid;
            return this;
        }

        public Builder holderPid(String holderPid) {
            msg.holderPid = holderPid;
            return this;
        }

        public Builder status(String status) {
            msg.status = status;
            return this;
        }

        public Builder rejectionReason(String rejectionReason) {
            msg.rejectionReason = rejectionReason;
            return this;
        }

        // New builder property for requestId
        public Builder requestId(String requestId) {
            msg.requestId = requestId;
            return this;
        }

        @JsonProperty("credentials")
        public Builder credentials(List<CredentialContainer> credentials) {
            if (credentials != null) {
                msg.credentials.clear();
                msg.credentials.addAll(credentials);
            }
            return this;
        }

        public CredentialMessage build() {
            try {
                msg.validateBase();
                if (msg.credentials == null) {
                    msg.credentials = new ArrayList<>();
                }
                return msg;
            } catch (Exception e) {
                throw new ValidationException("CredentialMessage - " + e.getMessage());
            }
        }
    }

    @JsonDeserialize(builder = CredentialContainer.Builder.class)
    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    @Getter
    public static class CredentialContainer implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @NotNull
        private String credentialType;

        private Object payload;

        @NotNull
        private String format;

        @JsonPOJOBuilder(withPrefix = "")
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Builder {
            private final CredentialContainer container;

            private Builder() {
                container = new CredentialContainer();
            }

            public static Builder newInstance() {
                return new Builder();
            }

            public Builder credentialType(String credentialType) {
                container.credentialType = credentialType;
                return this;
            }

            public Builder payload(Object payload) {
                container.payload = payload;
                return this;
            }

            public Builder format(String format) {
                container.format = format;
                return this;
            }

            public CredentialContainer build() {
                try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
                    Set<ConstraintViolation<CredentialContainer>> violations = vf.getValidator().validate(container);
                    if (violations.isEmpty()) {
                        return container;
                    }
                    throw new ValidationException("CredentialContainer - " +
                            violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(",")));
                }
            }
        }
    }
}
