package it.eng.dataplane.api.message;

import it.eng.dataplane.api.DataPlaneConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataFlowPrepareMetadataTest {

    @Test
    @DisplayName("Structured source and sink sections preserve nested groups")
    void structuredSectionsPreserveNestedGroups() {
        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("process-1")
                .transferType("stream:grpc")
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SOURCE, Map.of(
                                DataPlaneConstants.METADATA_FIELD_SOURCE_TYPE, "s3",
                                DataPlaneConstants.METADATA_FIELD_FINITE, "false",
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "source-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "dataset-1")),
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_FIELD_MODE, "VIEW",
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "sink-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "process-1"))))
                .build();

        DataFlowPrepareMetadata metadata = DataFlowPrepareMetadata.from(message);

        assertEquals("stream:grpc", metadata.getTransferType());
        assertEquals("s3", metadata.getSourceSection().getString(DataPlaneConstants.METADATA_FIELD_SOURCE_TYPE));
        assertEquals("false", metadata.getSourceSection().getString(DataPlaneConstants.METADATA_FIELD_FINITE));
        assertEquals("source-bucket", metadata.getSourceSection()
                .getSection(DataPlaneConstants.METADATA_SECTION_S3)
                .getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        assertEquals("dataset-1", metadata.getSourceSection()
                .getSection(DataPlaneConstants.METADATA_SECTION_S3)
                .getString(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
        assertEquals("VIEW", metadata.getSinkSection().getString(DataPlaneConstants.METADATA_FIELD_MODE));
        assertEquals("sink-bucket", metadata.getSinkSection()
                .getSection(DataPlaneConstants.METADATA_SECTION_S3)
                .getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        assertEquals("process-1", metadata.getSinkSection()
                .getSection(DataPlaneConstants.METADATA_SECTION_S3)
                .getString(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
    }

    @Test
    @DisplayName("Transfer type falls back to top-level field when metadata omits it")
    void transferTypeFallsBackToTopLevelField() {
        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("process-2")
                .transferType("stream:kafka")
                .metadata(Map.of())
                .build();

        DataFlowPrepareMetadata metadata = DataFlowPrepareMetadata.from(message);

        assertEquals("stream:kafka", metadata.getTransferType());
    }
}
