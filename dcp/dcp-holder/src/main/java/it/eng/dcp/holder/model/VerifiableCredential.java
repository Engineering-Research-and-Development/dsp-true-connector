package it.eng.dcp.holder.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.dcp.common.model.ProfileId;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mongo entity representing a stored Verifiable Credential for a holder.
 */
@Document(collection = "verifiable_credentials")
@JsonDeserialize(builder = VerifiableCredential.Builder.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VerifiableCredential implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Indexed
    @NotNull
    private String holderDid;

    @Indexed
    @NotNull
    private String credentialType;

    /**
     * Optional profile identifier (e.g. VC11_SL2021_JWT) used for homogeneity grouping.
     */
    private ProfileId profileId;

    /**
     * Issuance date of the credential.
     */
    private Instant issuanceDate;

    /**
     * Expiration date. An optional TTL index can be configured at the collection level to remove expired docs.
     */
    private Instant expirationDate;

    /**
     * Raw credential payload as JSON tree. Stored for later validation/signature checks.
     */
    @JsonProperty("credential")
    private JsonNode credential;

    /**
     * Convenience list of referenced credential ids (when credential is a wrapper) - preserved order.
     */
    @Size(min = 0)
    private List<String> credentialIds = new ArrayList<>();

    /**
     * The asserting Issuer DID that delivered this credential (recorded from validated delivery token).
     */
    private String issuerDid;

    /**
     * Optional credentialStatus for revocation checking (StatusList2021).
     * Structure: { type, statusListCredential, statusListIndex }
     */
    private JsonNode credentialStatus;

    /**
     * Optional JWT representation of this credential (compact JWT format).
     * When present, this can be embedded directly in a VP's verifiableCredential array
     * per DCP spec Section 5.4.2 Example 5 (path_nested with format jwt_vc).
     */
    private String jwtRepresentation;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final VerifiableCredential vc;

        private Builder() {
            vc = new VerifiableCredential();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            vc.id = id;
            return this;
        }

        public Builder holderDid(String holderDid) {
            vc.holderDid = holderDid;
            return this;
        }

        public Builder credentialType(String credentialType) {
            vc.credentialType = credentialType;
            return this;
        }

        public Builder profileId(ProfileId profileId) {
            vc.profileId = profileId;
            return this;
        }

        public Builder issuanceDate(Instant issuanceDate) {
            vc.issuanceDate = issuanceDate;
            return this;
        }

        public Builder expirationDate(Instant expirationDate) {
            vc.expirationDate = expirationDate;
            return this;
        }

        public Builder credential(JsonNode credential) {
            vc.credential = credential;
            return this;
        }

        public Builder credentialIds(List<String> credentialIds) {
            if (credentialIds != null) {
                vc.credentialIds.clear();
                vc.credentialIds.addAll(credentialIds);
            }
            return this;
        }
        public Builder credentialStatus(JsonNode credentialStatus) {
            vc.credentialStatus = credentialStatus;
            return this;
        }

        public Builder type(String type) {
            // Alias for credentialType for convenience
            vc.credentialType = type;
            return this;
        }

        public Builder issuer(String issuer) {
            // Alias for issuerDid for convenience
            vc.issuerDid = issuer;
            return this;
        }
        public Builder jwtRepresentation(String jwtRepresentation) {
            vc.jwtRepresentation = jwtRepresentation;
            return this;
        }

        public Builder issuerDid(String issuerDid) {
            vc.issuerDid = issuerDid;
            return this;
        }

        public VerifiableCredential build() {
            // generate id if missing
            if (vc.id == null) {
                vc.id = "urn:uuid:" + UUID.randomUUID();
            }

            try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
                Set<ConstraintViolation<VerifiableCredential>> violations = vf.getValidator().validate(vc);
                if (!violations.isEmpty()) {
                    throw new ValidationException("VerifiableCredential - " +
                            violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(",")));
                }
            }

            if (vc.credentialIds == null) vc.credentialIds = new ArrayList<>();
            return vc;
        }
    }
}
