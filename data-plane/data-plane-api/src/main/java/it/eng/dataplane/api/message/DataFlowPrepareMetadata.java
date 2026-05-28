package it.eng.dataplane.api.message;

import it.eng.dataplane.api.DataPlaneConstants;

import java.util.Map;

/**
 * Typed accessor for structured prepare-time metadata.
 */
public final class DataFlowPrepareMetadata {

    private final String topLevelTransferType;
    private final Map<String, Object> metadata;

    private DataFlowPrepareMetadata(String topLevelTransferType, Map<String, Object> metadata) {
        this.topLevelTransferType = topLevelTransferType;
        this.metadata = metadata == null ? Map.of() : metadata;
    }

    /**
     * Creates typed metadata accessors for the given prepare message.
     *
     * @param message the prepare message to inspect
     * @return typed metadata accessor
     */
    public static DataFlowPrepareMetadata from(DataFlowPrepareMessage message) {
        return new DataFlowPrepareMetadata(message.getTransferType(), message.getMetadata());
    }

    /**
     * Returns the prepare-time transfer type hint.
     *
     * @return transfer type, or {@code null} when absent
     */
    public String getTransferType() {
        String metadataTransferType = asText(metadata.get(DataPlaneConstants.METADATA_FIELD_TRANSFER_TYPE));
        return metadataTransferType != null ? metadataTransferType : topLevelTransferType;
    }

    /**
     * Returns the structured source metadata section.
     *
     * @return source section view
     */
    public DataFlowPrepareMetadataSection getSourceSection() {
        return getSection(DataPlaneConstants.METADATA_SECTION_SOURCE);
    }

    /**
     * Returns the structured sink metadata section.
     *
     * @return sink section view
     */
    public DataFlowPrepareMetadataSection getSinkSection() {
        return getSection(DataPlaneConstants.METADATA_SECTION_SINK);
    }

    /**
     * Returns scalar source metadata fields.
     *
     * @return immutable map of direct source fields
     */
    public Map<String, String> getSourceProperties() {
        return getScalarProperties(DataPlaneConstants.METADATA_SECTION_SOURCE);
    }

    /**
     * Returns scalar sink metadata fields.
     *
     * @return immutable map of direct sink fields
     */
    public Map<String, String> getSinkProperties() {
        return getScalarProperties(DataPlaneConstants.METADATA_SECTION_SINK);
    }

    private DataFlowPrepareMetadataSection getSection(String sectionName) {
        Object value = metadata.get(sectionName);
        if (!(value instanceof Map<?, ?> section)) {
            return new DataFlowPrepareMetadataSection(Map.of());
        }
        Map<String, Object> normalized = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            normalized.put(key, entry.getValue());
        }
        return new DataFlowPrepareMetadataSection(normalized);
    }

    private Map<String, String> getScalarProperties(String sectionName) {
        Object value = metadata.get(sectionName);
        if (!(value instanceof Map<?, ?> section)) {
            return Map.of();
        }

        Map<String, String> scalarSection = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            String textValue = asText(entry.getValue());
            if (textValue != null) {
                scalarSection.put(key, textValue);
            }
        }
        return Map.copyOf(scalarSection);
    }

    private String asText(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return null;
        }
        return String.valueOf(value);
    }
}
