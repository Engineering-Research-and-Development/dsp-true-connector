package it.eng.dataplane.api.io;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Immutable context for opening a source stream.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SourceContext {

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
     * Builder for {@link SourceContext}.
     */
    public static class Builder {
        private final SourceContext instance = new SourceContext();

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
         * Sets the source properties.
         *
         * @param properties source properties
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
        public SourceContext build() {
            if (instance.properties == null) {
                instance.properties = Map.of();
            }
            return instance;
        }
    }
}
