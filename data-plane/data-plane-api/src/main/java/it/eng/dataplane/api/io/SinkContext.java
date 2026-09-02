package it.eng.dataplane.api.io;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Immutable context for writing to a sink.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SinkContext {

    private Map<String, String> properties;

    /**
     * Returns the property value for the given key.
     *
     * @param key property key
     * @return property value or {@code null} when absent
     */
    public String getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Builder for {@link SinkContext}.
     */
    public static class Builder {
        private final SinkContext instance = new SinkContext();

        private Builder() {
        }

        /**
         * Creates a new builder instance.
         *
         * @return new builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets the sink properties.
         *
         * @param properties sink properties
         * @return this builder
         */
        public Builder properties(Map<String, String> properties) {
            instance.properties = properties == null ? Map.of() : Map.copyOf(properties);
            return this;
        }

        /**
         * Builds the immutable context.
         *
         * @return built context
         */
        public SinkContext build() {
            if (instance.properties == null) {
                instance.properties = Map.of();
            }
            return instance;
        }
    }
}
