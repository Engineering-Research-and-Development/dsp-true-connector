package it.eng.dcp.issuer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.dcp.common.model.ProfileId;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A status list of the Issuer (StatusList2021 or BitstringStatusList, chosen by the credential profile).
 *
 * <p>Allocation and status are tracked separately: {@code nextIndex} counts the entries handed out to issued
 * credentials and is never decremented, so an index is never reused; the {@code bitstring} holds the status bits,
 * where 0 means the credential is valid and 1 means it is revoked. The bitstring is kept in the order the
 * status list specifications define: index 0 is the left-most (most significant) bit of the first byte.
 */
@Getter
@JsonDeserialize(builder = StatusList.Builder.class)
@Document(collection = "status_lists")
public class StatusList implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final int BITS_PER_BYTE = 8;

    /** The list ID, the last path segment of the public status list URL. */
    @Id
    @NotNull
    private String id;

    /** The public URL that serves the status list credential; credentials reference it as statusListCredential. */
    @NotNull
    private String url;

    @NotNull
    private String issuerDid;

    /** The status purpose, for example {@code revocation}. */
    @NotNull
    private String statusPurpose;

    /** The credential profile whose status list format this list uses. */
    @NotNull
    private ProfileId profile;

    /** The number of status entries (bits) in the list. */
    private int size;

    /** The next index to allocate; indices below it are allocated and never reused. */
    private int nextIndex;

    /** The status bits, index 0 at the left-most bit of the first byte. */
    private byte[] bitstring;

    /** The signed status list credential (compact JWT) last published for this list. */
    private String signedCredential;

    private Instant createdAt;

    @Version
    private Long version;

    private StatusList() {
    }

    /**
     * Tells whether every index of the list has been allocated.
     *
     * @return true when no index is left to allocate
     */
    public boolean isFull() {
        return nextIndex >= size;
    }

    /**
     * Allocates the next free index. The status bit of the index stays 0 (valid).
     *
     * @return the allocated index
     * @throws IllegalStateException if the list is full
     */
    public int allocateIndex() {
        if (isFull()) {
            throw new IllegalStateException("Status list " + id + " is full");
        }
        return nextIndex++;
    }

    /**
     * Tells whether the index has been handed out to a credential.
     *
     * @param index the status list index
     * @return true when the index is allocated
     */
    public boolean isAllocated(int index) {
        return index >= 0 && index < nextIndex;
    }

    /**
     * Returns the status bit of the index.
     *
     * @param index the status list index
     * @return true when the bit is 1 (for the revocation purpose: revoked)
     * @throws IllegalArgumentException if the index is outside the list
     */
    public boolean isSet(int index) {
        checkInRange(index);
        return (bitstring[index / BITS_PER_BYTE] & mask(index)) != 0;
    }

    /**
     * Sets the status bit of an allocated index to 1. Revocation is not reversible, so there is no way back to 0.
     *
     * @param index the status list index
     * @throws IllegalArgumentException if the index has not been allocated
     */
    public void setBit(int index) {
        if (!isAllocated(index)) {
            throw new IllegalArgumentException("Status list index " + index + " is not allocated");
        }
        bitstring[index / BITS_PER_BYTE] |= mask(index);
    }

    /**
     * Returns a copy of the status bits.
     *
     * @return the bitstring, index 0 at the left-most bit of the first byte
     */
    public byte[] getBitstring() {
        return Arrays.copyOf(bitstring, bitstring.length);
    }

    /**
     * Stores the signed status list credential to publish.
     *
     * @param signedCredential the compact JWT of the status list credential
     */
    public void setSignedCredential(String signedCredential) {
        this.signedCredential = signedCredential;
    }

    private void checkInRange(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Status list index " + index + " is outside the list");
        }
    }

    private static int mask(int index) {
        return 1 << (BITS_PER_BYTE - 1 - index % BITS_PER_BYTE);
    }

    /**
     * Builder for {@link StatusList}.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final StatusList list;

        private Builder() {
            list = new StatusList();
        }

        /**
         * Creates a new builder.
         *
         * @return the builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets the list ID.
         *
         * @param id the list ID
         * @return this builder
         */
        public Builder id(String id) {
            list.id = id;
            return this;
        }

        /**
         * Sets the public status list credential URL.
         *
         * @param url the URL
         * @return this builder
         */
        public Builder url(String url) {
            list.url = url;
            return this;
        }

        /**
         * Sets the issuer DID.
         *
         * @param issuerDid the issuer DID
         * @return this builder
         */
        public Builder issuerDid(String issuerDid) {
            list.issuerDid = issuerDid;
            return this;
        }

        /**
         * Sets the status purpose.
         *
         * @param statusPurpose the status purpose
         * @return this builder
         */
        public Builder statusPurpose(String statusPurpose) {
            list.statusPurpose = statusPurpose;
            return this;
        }

        /**
         * Sets the credential profile.
         *
         * @param profile the profile
         * @return this builder
         */
        public Builder profile(ProfileId profile) {
            list.profile = profile;
            return this;
        }

        /**
         * Sets the number of entries; it must be a positive multiple of 8.
         *
         * @param size the number of entries
         * @return this builder
         */
        public Builder size(int size) {
            list.size = size;
            return this;
        }

        /**
         * Sets the next index to allocate.
         *
         * @param nextIndex the next index
         * @return this builder
         */
        public Builder nextIndex(int nextIndex) {
            list.nextIndex = nextIndex;
            return this;
        }

        /**
         * Sets the status bits.
         *
         * @param bitstring the bitstring, index 0 at the left-most bit of the first byte
         * @return this builder
         */
        public Builder bitstring(byte[] bitstring) {
            list.bitstring = bitstring;
            return this;
        }

        /**
         * Sets the signed status list credential.
         *
         * @param signedCredential the compact JWT
         * @return this builder
         */
        public Builder signedCredential(String signedCredential) {
            list.signedCredential = signedCredential;
            return this;
        }

        /**
         * Sets the creation time.
         *
         * @param createdAt the creation time
         * @return this builder
         */
        public Builder createdAt(Instant createdAt) {
            list.createdAt = createdAt;
            return this;
        }

        /**
         * Sets the optimistic locking version.
         *
         * @param version the version
         * @return this builder
         */
        public Builder version(Long version) {
            list.version = version;
            return this;
        }

        /**
         * Validates and builds the status list. A missing bitstring is created with every bit 0.
         *
         * @return the status list
         * @throws ValidationException if a required field is missing or the size, index or bitstring is invalid
         */
        public StatusList build() {
            if (list.size <= 0 || list.size % BITS_PER_BYTE != 0) {
                throw new ValidationException("StatusList - size must be a positive multiple of " + BITS_PER_BYTE);
            }
            if (list.nextIndex < 0 || list.nextIndex > list.size) {
                throw new ValidationException("StatusList - nextIndex must be between 0 and size");
            }
            if (list.bitstring == null) {
                list.bitstring = new byte[list.size / BITS_PER_BYTE];
            } else if (list.bitstring.length != list.size / BITS_PER_BYTE) {
                throw new ValidationException("StatusList - bitstring length does not match size");
            }
            if (list.createdAt == null) {
                list.createdAt = Instant.now();
            }
            try (var vf = Validation.buildDefaultValidatorFactory()) {
                Set<ConstraintViolation<StatusList>> violations = vf.getValidator().validate(list);
                if (!violations.isEmpty()) {
                    throw new ValidationException("StatusList - " + violations.stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(",")));
                }
            }
            return list;
        }
    }
}
