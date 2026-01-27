package it.eng.dcp.holder.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@JsonDeserialize(builder = ConsentRecord.Builder.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConsentRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    private String id;

    @NotNull
    private String holderDid;

    @NotNull
    @Size(min = 1)
    private List<String> requested = new ArrayList<>();

    private final List<String> granted = new ArrayList<>();

    private Instant issuedAt;

    private Instant expiresAt;

    public boolean isExpired(Instant now) {
        if (expiresAt == null) return false;
        return !expiresAt.isAfter(now);
    }

    public boolean isExpired() {
        return isExpired(Instant.now());
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final ConsentRecord rec;

        private Builder() {
            rec = new ConsentRecord();
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            rec.id = id;
            return this;
        }

        public Builder holderDid(String holderDid) {
            rec.holderDid = holderDid;
            return this;
        }

        @JsonProperty("requested")
        public Builder requested(List<String> requested) {
            if (requested != null) {
                rec.requested.clear();
                rec.requested.addAll(requested);
            }
            return this;
        }

        @JsonProperty("granted")
        public Builder granted(List<String> granted) {
            if (granted != null) {
                rec.granted.clear();
                rec.granted.addAll(granted);
            }
            return this;
        }

        public Builder issuedAt(Instant issuedAt) {
            rec.issuedAt = issuedAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            rec.expiresAt = expiresAt;
            return this;
        }

        public ConsentRecord build() {
            // generate id if missing
            if (rec.id == null) {
                rec.id = "urn:uuid:" + UUID.randomUUID();
            }

            if (rec.issuedAt == null) {
                rec.issuedAt = Instant.now();
            }

            try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
                Set<ConstraintViolation<ConsentRecord>> violations = vf.getValidator().validate(rec);
                if (!violations.isEmpty()) {
                    throw new ValidationException("ConsentRecord - " +
                            violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(",")));
                }
            }

            // business rule: granted must be subset of requested
            if (!rec.granted.isEmpty()) {
                java.util.Set<String> reqSet = new java.util.HashSet<>(rec.requested);
                for (String g : rec.granted) {
                    if (!reqSet.contains(g)) {
                        throw new ValidationException("ConsentRecord - granted must be subset of requested");
                    }
                }
            }

            return rec;
        }
    }
}
