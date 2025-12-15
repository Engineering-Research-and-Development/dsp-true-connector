package it.eng.dcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Model representing a Verifiable Presentation used by the holder to present one or more credentials.
 */
@Document(collection = "verifiable_presentations")
@JsonDeserialize(builder = VerifiablePresentation.Builder.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VerifiablePresentation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    private String holderDid;

    /**
     * Ordered list of credential ids contained in this presentation.
     */
    @Size(min = 1)
    private List<String> credentialIds = new ArrayList<>();

    /**
     * Full credentials to be embedded in the presentation.
     * Can contain JWT strings (for jwt format) or JSON objects (for json-ld format).
     * Per W3C VC Data Model and DCP spec, verifiableCredential can be:
     * - URIs/IDs: ["urn:uuid:abc"]
     * - Embedded VCs: ["eyJhbGc...JWT...", {...JSON-LD VC...}]
     * If present, this takes precedence over credentialIds in the VP claim.
     */
    private List<Object> credentials = new ArrayList<>();

    /**
     * Profile identifier used for homogeneity (e.g. VC11_SL2021_JWT).
     */
    @NotNull
    private String profileId;


    /**
     * Proof block or JWT string depending on format. For JSON-LD, this is the proof object; for JWT, it may contain
     * the compact JWT string.
     */
    private JsonNode proof;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final VerifiablePresentation vp;

        private Builder() {
            vp = new VerifiablePresentation();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            vp.id = id;
            return this;
        }

        public Builder holderDid(String holderDid) {
            vp.holderDid = holderDid;
            return this;
        }

        public Builder credentials(List<Object> credentials) {
            if (credentials != null) {
                vp.credentials.clear();
                vp.credentials.addAll(credentials);
            }
            return this;
        }

        public Builder credentialIds(List<String> credentialIds) {
            if (credentialIds != null) {
                vp.credentialIds.clear();
                vp.credentialIds.addAll(credentialIds);
            }
            return this;
        }

        public Builder profileId(String profileId) {
            vp.profileId = profileId;
            return this;
        }

        public Builder proof(JsonNode proof) {
            vp.proof = proof;
            return this;
        }

        public VerifiablePresentation build() {
            if (vp.id == null) vp.id = "urn:uuid:" + UUID.randomUUID();

            try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
                Set<ConstraintViolation<VerifiablePresentation>> violations = vf.getValidator().validate(vp);
                if (!violations.isEmpty()) {
                    throw new ValidationException("VerifiablePresentation - " +
                            violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(",")));
                }
            }

            if (vp.credentialIds == null) vp.credentialIds = new ArrayList<>();
            return vp;
        }
    }
}
