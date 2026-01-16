package it.eng.dcp.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.time.Instant;
import java.util.BitSet;

/**
 * Data model for a cached status list credential.
 * Contains the parsed credential payload, decoded bit array, and fetch timestamp.
 */
@Getter
public class CachedStatusList {
    private JsonNode vcPayload;
    private BitSet decodedBits;
    private Instant fetchedAt;

    private CachedStatusList() {
    }

    public static class Builder {
        private final CachedStatusList cached;

        private Builder() {
            cached = new CachedStatusList();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder vcPayload(JsonNode vcPayload) {
            cached.vcPayload = vcPayload;
            return this;
        }

        public Builder decodedBits(BitSet decodedBits) {
            cached.decodedBits = decodedBits;
            return this;
        }

        public Builder fetchedAt(Instant fetchedAt) {
            cached.fetchedAt = fetchedAt;
            return this;
        }

        public CachedStatusList build() {
            if (cached.decodedBits == null) {
                throw new IllegalArgumentException("CachedStatusList: decodedBits is required");
            }
            if (cached.fetchedAt == null) {
                throw new IllegalArgumentException("CachedStatusList: fetchedAt is required");
            }
            return cached;
        }
    }
}

