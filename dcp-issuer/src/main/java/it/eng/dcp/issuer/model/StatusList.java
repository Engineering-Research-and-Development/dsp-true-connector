package it.eng.dcp.issuer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.util.BitSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a status list for verifiable credentials (StatusList2021/BitstringStatusList).
 */
@Getter
@JsonDeserialize(builder = StatusList.Builder.class)
@Document(collection = "status_lists")
public class StatusList implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id; // e.g., issuerDid + "/status/1"
    private byte[] bitstring;
    private int size;
    private String statusPurpose; // e.g., "revocation"
    private String issuerDid;

    private StatusList() {}

    // Helper to get BitSet from byte[]
    public BitSet getBitSet() {
        return bitstring != null ? BitSet.valueOf(bitstring) : new BitSet(size);
    }
    // Helper to set bitstring from BitSet
    public void setBitSet(BitSet bits) {
        this.bitstring = bits != null ? bits.toByteArray() : new byte[(size+7)/8];
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final StatusList list;
        private Builder() { list = new StatusList(); }
        public static Builder newInstance() { return new Builder(); }
        public Builder id(String id) { list.id = id; return this; }
        public Builder bitstring(byte[] bitstring) { list.bitstring = bitstring; return this; }
        public Builder size(int size) { list.size = size; return this; }
        public Builder statusPurpose(String statusPurpose) { list.statusPurpose = statusPurpose; return this; }
        public Builder issuerDid(String issuerDid) { list.issuerDid = issuerDid; return this; }
        public StatusList build() {
            try (var vf = Validation.buildDefaultValidatorFactory()) {
                Set<ConstraintViolation<StatusList>> violations = vf.getValidator().validate(list);
                if (list.id == null || list.id.isBlank()) throw new IllegalArgumentException("StatusList - id is required");
                if (list.issuerDid == null || list.issuerDid.isBlank()) throw new IllegalArgumentException("StatusList - issuerDid is required");
                if (list.statusPurpose == null || list.statusPurpose.isBlank()) throw new IllegalArgumentException("StatusList - statusPurpose is required");
                if (list.size <= 0) throw new IllegalArgumentException("StatusList - size must be positive");
                if (list.bitstring == null) list.bitstring = new byte[(list.size+7)/8];
                if (!violations.isEmpty()) {
                    throw new ValidationException("StatusList - " +
                            violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(",")));
                }
                return list;
            }
        }
    }
}
