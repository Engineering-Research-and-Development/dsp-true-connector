package it.eng.dataplane.api.message;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable typed view over a structured prepare metadata section.
 */
public final class DataFlowPrepareMetadataSection {

    private final Map<String, Object> values;

    DataFlowPrepareMetadataSection(Map<String, Object> values) {
        this.values = values == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(values));
    }

    /**
     * Returns a direct scalar field from this section.
     *
     * @param fieldName field name
     * @return scalar value, or {@code null} when absent or non-scalar
     */
    public String getString(String fieldName) {
        Object value = values.get(fieldName);
        if (value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return null;
        }
        return String.valueOf(value);
    }

    /**
     * Returns a nested metadata section.
     *
     * @param sectionName nested section name
     * @return nested section view, or an empty section when absent
     */
    public DataFlowPrepareMetadataSection getSection(String sectionName) {
        Object value = values.get(sectionName);
        if (!(value instanceof Map<?, ?> map)) {
            return new DataFlowPrepareMetadataSection(Map.of());
        }
        Map<String, Object> nested = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                nested.put(key, entry.getValue());
            }
        }
        return new DataFlowPrepareMetadataSection(nested);
    }

    /**
     * Returns direct scalar values in this section.
     *
     * @return immutable scalar property map
     */
    public Map<String, String> toScalarMap() {
        Map<String, String> scalarValues = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
                continue;
            }
            scalarValues.put(entry.getKey(), String.valueOf(value));
        }
        return Map.copyOf(scalarValues);
    }

    /**
     * Returns whether this section is empty.
     *
     * @return {@code true} when the section has no fields
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }
}
