package it.eng.dcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

import jakarta.validation.ValidationException;

/**
 * Holder-side record tracking the status of a credential issuance request.
 */
@Document(collection = "credential_status_records")
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CredentialStatusRecord {

    @Id
    private String id;

    /**
     * The request identifier (issuer-assigned) used to correlate issuance status.
     */
    private String requestId;

    /**
     * Issuer PID that created the request (optional).
     */
    private String issuerPid;

    /**
     * Holder PID for which credentials are being issued (often a DID).
     */
    private String holderPid;

    /**
     * Current status of the issuance request (PENDING / ISSUED / REJECTED).
     */
    private CredentialStatus status = CredentialStatus.PENDING;

    /**
     * Optional rejection reason when status == REJECTED.
     */
    private String rejectionReason;

    /**
     * Number of credential containers persisted (if ISSUED).
     */
    private Integer savedCount = 0;

    private Instant createdAt = Instant.now();

    private Instant updatedAt = Instant.now();

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final CredentialStatusRecord rec;

        private Builder() {
            rec = new CredentialStatusRecord();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder requestId(String requestId) {
            rec.requestId = requestId;
            return this;
        }

        public Builder issuerPid(String issuerPid) {
            rec.issuerPid = issuerPid;
            return this;
        }

        public Builder holderPid(String holderPid) {
            rec.holderPid = holderPid;
            return this;
        }

        public Builder status(CredentialStatus status) {
            rec.status = status;
            return this;
        }

        public Builder rejectionReason(String rejectionReason) {
            rec.rejectionReason = rejectionReason;
            return this;
        }

        public Builder savedCount(Integer savedCount) {
            rec.savedCount = savedCount;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            rec.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            rec.updatedAt = updatedAt;
            return this;
        }

        public CredentialStatusRecord build() {
            // basic validation
            if (rec.requestId == null || rec.requestId.isBlank()) {
                throw new ValidationException("CredentialStatusRecord - requestId is required");
            }
            if (rec.status == null) {
                rec.status = CredentialStatus.PENDING;
            }
            if (rec.createdAt == null) rec.createdAt = Instant.now();
            rec.updatedAt = Instant.now();
            return rec;
        }
    }
}

