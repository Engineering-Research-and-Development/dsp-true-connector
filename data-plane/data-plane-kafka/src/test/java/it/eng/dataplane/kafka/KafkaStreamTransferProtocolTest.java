package it.eng.dataplane.kafka;

import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.s3.service.FiniteArtifactViewPrepareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaStreamTransferProtocolTest {

    @Test
    @DisplayName("prepare() in VIEW mode returns presignedUrl and skips source publication setup")
    void prepare_viewMode_returnsPresignedUrlAndSkipsTransportPreparation() {
        FiniteArtifactViewPrepareService helper = mock(FiniteArtifactViewPrepareService.class);
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol(helper);

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-kafka-view")
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_FIELD_MODE, DataPlaneConstants.METADATA_MODE_VIEW,
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "tp-kafka-view",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "bucket-access",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "bucket-secret",
                                        DataPlaneConstants.METADATA_S3_REGION, "us-east-1"))))
                .build();

        when(helper.isViewRequest(message)).thenReturn(true);
        when(helper.prepareViewResponse("stream:kafka", message))
                .thenReturn(DataFlowPrepareResponse.Builder.newInstance()
                        .processId("tp-kafka-view")
                        .dataAddress(Map.of(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY, "https://example.test/kafka-view"))
                        .build());

        DataFlowPrepareResponse response = protocol.prepare(message);

        assertEquals("https://example.test/kafka-view",
                response.getDataAddress().get(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY));
        verify(helper).prepareViewResponse("stream:kafka", message);
    }

    @Test
    @DisplayName("prepare() in VIEW mode propagates non-finite rejection")
    void prepare_viewMode_nonFinite_rethrowsClearException() {
        FiniteArtifactViewPrepareService helper = mock(FiniteArtifactViewPrepareService.class);
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol(helper);

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-kafka-non-finite-view")
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SOURCE, Map.of(DataPlaneConstants.METADATA_FIELD_FINITE, "false"),
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_FIELD_MODE, DataPlaneConstants.METADATA_MODE_VIEW)))
                .build();

        when(helper.isViewRequest(message)).thenReturn(true);
        when(helper.prepareViewResponse("stream:kafka", message))
                .thenThrow(new IllegalArgumentException(
                        "VIEW mode is supported only for finite materialized artifacts; non-finite stream view is not implemented for stream:kafka"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> protocol.prepare(message));
        assertEquals("VIEW mode is supported only for finite materialized artifacts; non-finite stream view is not implemented for stream:kafka",
                exception.getMessage());
    }
}
