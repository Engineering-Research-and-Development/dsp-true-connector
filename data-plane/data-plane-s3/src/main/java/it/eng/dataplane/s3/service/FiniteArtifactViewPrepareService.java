package it.eng.dataplane.s3.service;

import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareMetadata;
import it.eng.dataplane.api.message.DataFlowPrepareMetadataSection;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prepares VIEW responses for finite materialized artifacts stored in S3.
 */
@Service
public class FiniteArtifactViewPrepareService {

    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofDays(7L);

    private final S3ClientService s3ClientService;

    /**
     * Creates the helper with its S3 dependency.
     *
     * @param s3ClientService the S3 client service used to create presigned URLs
     */
    public FiniteArtifactViewPrepareService(S3ClientService s3ClientService) {
        this.s3ClientService = s3ClientService;
    }

    /**
     * Returns whether the prepare request asks for VIEW mode.
     *
     * @param message the prepare message to inspect
     * @return {@code true} when sink.mode is {@code VIEW}
     */
    public boolean isViewRequest(DataFlowPrepareMessage message) {
        return DataPlaneConstants.METADATA_MODE_VIEW.equals(
                DataFlowPrepareMetadata.from(message)
                        .getSinkSection()
                        .getString(DataPlaneConstants.METADATA_FIELD_MODE));
    }

    /**
     * Builds a prepare response containing a presigned URL for a finite materialized artifact.
     * <p>The incoming {@code sink.s3} section may contain two endpoint values:
     * {@code endpointOverride} for dataplane-to-S3 SDK calls and
     * {@code publicPresignedEndpoint} for the URL returned to the caller.
     * he helper forwards the full scalar map to {@link S3ClientService} so the shared S3 layer
     * can interpret the split consistently for HTTP and streaming dataplanes.</p>
     *
     * @param protocolId the transport protocol identifier used in error messages
     * @param message the prepare message to inspect
     * @return a prepare response with {@code dataAddress.presignedUrl}
     * @throws IllegalArgumentException when the request declares a non-finite source
     */
    public DataFlowPrepareResponse prepareViewResponse(String protocolId, DataFlowPrepareMessage message) {
        DataFlowPrepareMetadata metadata = DataFlowPrepareMetadata.from(message);
        assertFiniteMaterializedArtifactViewSupported(protocolId, metadata);

        DataFlowPrepareMetadataSection sinkS3 = metadata.getSinkSection()
                .getSection(DataPlaneConstants.METADATA_SECTION_S3);
        Map<String, String> sinkProperties = sinkS3.toScalarMap();
//        Map<String, String> sinkProperties = buildViewPresignProperties(sinkS3);
        String presignedUrl = s3ClientService.generateGetPresignedUrl(sinkProperties, PRESIGNED_URL_EXPIRATION);

        return DataFlowPrepareResponse.Builder.newInstance()
                .processId(message.getProcessId())
                .dataAddress(Map.of(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY, presignedUrl))
                .build();
    }

    /**
     * Rejects VIEW requests for explicitly non-finite sources.
     *
     * @param protocolId the transport protocol identifier used in error messages
     * @param metadata the structured prepare metadata
     * @throws IllegalArgumentException when source.finite is explicitly false
     */
    private void assertFiniteMaterializedArtifactViewSupported(String protocolId, DataFlowPrepareMetadata metadata) {
        String finite = metadata.getSourceSection().getString(DataPlaneConstants.METADATA_FIELD_FINITE);
        if (StringUtils.equalsIgnoreCase("false", finite)) {
            throw new IllegalArgumentException(
                    "VIEW mode is supported only for finite materialized artifacts; non-finite stream view is not implemented for "
                            + protocolId);
        }
    }

    /**
     * Builds the S3 property map used by shared VIEW presigning.
     *
     * <p>The dataplane must pass through both the internal {@code endpointOverride} used for
     * server-side S3 access and the optional {@code publicPresignedEndpoint} used for the
     * browser-facing URL returned to the caller.</p>
     *
     * @param sinkS3 the structured {@code sink.s3} metadata section
     * @return S3 properties for {@link S3ClientService#generateGetPresignedUrl(Map, Duration)}
     */
    private Map<String, String> buildViewPresignProperties(DataFlowPrepareMetadataSection sinkS3) {
        Map<String, String> sinkProperties = new LinkedHashMap<>();
        putIfNotBlank(sinkProperties, S3Utils.BUCKET_NAME,
                sinkS3.getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        putIfNotBlank(sinkProperties, S3Utils.OBJECT_KEY,
                sinkS3.getString(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
        putIfNotBlank(sinkProperties, S3Utils.ACCESS_KEY,
                sinkS3.getString(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        putIfNotBlank(sinkProperties, S3Utils.SECRET_KEY,
                sinkS3.getString(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        putIfNotBlank(sinkProperties, S3Utils.REGION,
                sinkS3.getString(DataPlaneConstants.METADATA_S3_REGION));
        putIfNotBlank(sinkProperties, S3Utils.ENDPOINT_OVERRIDE,
                sinkS3.getString(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
        putIfNotBlank(sinkProperties, S3Utils.PUBLIC_PRESIGNED_ENDPOINT,
                sinkS3.getString(DataPlaneConstants.METADATA_S3_PUBLIC_PRESIGNED_ENDPOINT));
        return Map.copyOf(sinkProperties);
    }

    private void putIfNotBlank(Map<String, String> target, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.put(key, value);
        }
    }
}
