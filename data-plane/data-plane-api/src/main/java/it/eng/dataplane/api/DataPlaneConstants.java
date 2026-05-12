package it.eng.dataplane.api;

/**
 * Common DSP/DCAT JSON-LD constants used by Data Plane API message classes.
 */
public final class DataPlaneConstants {

    /** The JSON-LD {@code @context} property key. */
    public static final String CONTEXT = "@context";

    /** The JSON-LD {@code @type} property key. */
    public static final String TYPE = "@type";

    /** The DSP 2025-1 JSON-LD context URL. */
    public static final String DSPACE_2025_01_CONTEXT = "https://w3id.org/dspace/2025/1/context.jsonld";

    private DataPlaneConstants() {
    }
}
