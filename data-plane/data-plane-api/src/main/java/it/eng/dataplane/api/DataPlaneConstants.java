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

    /** Metadata section name for source-side transfer details. */
    public static final String METADATA_SECTION_SOURCE = "source";

    /** Metadata section name for sink-side transfer details. */
    public static final String METADATA_SECTION_SINK = "sink";

    /** Top-level metadata key for prepare-time transfer routing. */
    public static final String METADATA_FIELD_TRANSFER_TYPE = "transferType";

    /** Metadata field name for source type hints. */
    public static final String METADATA_FIELD_SOURCE_TYPE = "sourceType";

    /** Metadata field name for finite/non-finite source hints. */
    public static final String METADATA_FIELD_FINITE = "finite";

    /** Metadata field name for sink mode hints. */
    public static final String METADATA_FIELD_MODE = "mode";

    /** Metadata value used when the sink flow is a presigned VIEW operation. */
    public static final String METADATA_MODE_VIEW = "VIEW";

    /** Metadata section name for S3-specific transfer details. */
    public static final String METADATA_SECTION_S3 = "s3";

    /** S3 metadata key for the bucket name. */
    public static final String METADATA_S3_BUCKET_NAME = "bucketName";

    /** S3 metadata key for the object key. */
    public static final String METADATA_S3_OBJECT_KEY = "objectKey";

    /** S3 metadata key for the AWS/MinIO region. */
    public static final String METADATA_S3_REGION = "region";

    /** S3 metadata key for the access key. */
    public static final String METADATA_S3_ACCESS_KEY = "accessKey";

    /** S3 metadata key for the secret key. */
    public static final String METADATA_S3_SECRET_KEY = "secretKey";

    /** S3 metadata key for the endpoint override. */
    public static final String METADATA_S3_ENDPOINT_OVERRIDE = "endpointOverride";

    /** Data address field for the transfer endpoint. */
    public static final String DATA_ADDRESS_FIELD_ENDPOINT = "endpoint";

    /** Data address field for the transfer endpoint type. */
    public static final String DATA_ADDRESS_FIELD_ENDPOINT_TYPE = "endpointType";

    /** Data address endpoint property for sink bucket name. */
    public static final String DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME =
            METADATA_SECTION_SINK + "." + METADATA_S3_BUCKET_NAME;

    /** Data address endpoint property for sink object key. */
    public static final String DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY =
            METADATA_SECTION_SINK + "." + METADATA_S3_OBJECT_KEY;

    /** Data address endpoint property for sink region. */
    public static final String DATA_ADDRESS_PROPERTY_SINK_REGION =
            METADATA_SECTION_SINK + "." + METADATA_S3_REGION;

    /** Data address endpoint property for sink access key. */
    public static final String DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY =
            METADATA_SECTION_SINK + "." + METADATA_S3_ACCESS_KEY;

    /** Data address endpoint property for sink secret key. */
    public static final String DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY =
            METADATA_SECTION_SINK + "." + METADATA_S3_SECRET_KEY;

    /** Data address endpoint property for sink endpoint override. */
    public static final String DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE =
            METADATA_SECTION_SINK + "." + METADATA_S3_ENDPOINT_OVERRIDE;

    /** Data address endpoint property for source bucket name. */
    public static final String DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME =
            METADATA_SECTION_SOURCE + "." + METADATA_S3_BUCKET_NAME;

    /** Data address endpoint property for source object key. */
    public static final String DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY =
            METADATA_SECTION_SOURCE + "." + METADATA_S3_OBJECT_KEY;

    /** Data address endpoint property for source region. */
    public static final String DATA_ADDRESS_PROPERTY_SOURCE_REGION =
            METADATA_SECTION_SOURCE + "." + METADATA_S3_REGION;

    /** Data address endpoint property for source access key. */
    public static final String DATA_ADDRESS_PROPERTY_SOURCE_ACCESS_KEY =
            METADATA_SECTION_SOURCE + "." + METADATA_S3_ACCESS_KEY;

    /** Data address endpoint property for source secret key. */
    public static final String DATA_ADDRESS_PROPERTY_SOURCE_SECRET_KEY =
            METADATA_SECTION_SOURCE + "." + METADATA_S3_SECRET_KEY;

    /** Data address endpoint property for source endpoint override. */
    public static final String DATA_ADDRESS_PROPERTY_SOURCE_ENDPOINT_OVERRIDE =
            METADATA_SECTION_SOURCE + "." + METADATA_S3_ENDPOINT_OVERRIDE;

    /** Data address key for the presigned URL returned by an HTTP-PULL prepare response. */
    public static final String DATA_ADDRESS_PRESIGNED_URL_KEY = "presignedUrl";

    private DataPlaneConstants() {
    }
}
