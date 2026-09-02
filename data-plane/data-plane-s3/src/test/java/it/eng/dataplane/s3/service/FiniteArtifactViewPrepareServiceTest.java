package it.eng.dataplane.s3.service;

import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.util.S3Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiniteArtifactViewPrepareServiceTest {

    @Mock
    private S3ClientService s3ClientService;

    @ParameterizedTest
    @CsvSource({
            "VIEW, true",
            "PUT, false"
    })
    @DisplayName("isViewRequest returns true only for VIEW mode")
    void isViewRequest_returnsExpectedValue(String mode, boolean expected) {
        FiniteArtifactViewPrepareService service = new FiniteArtifactViewPrepareService(s3ClientService);
        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-view-check")
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_FIELD_MODE, mode)))
                .build();

        assertThat(service.isViewRequest(message)).isEqualTo(expected);
    }

    @Test
    @DisplayName("prepareViewResponse returns presignedUrl for finite materialized artifact view")
    void prepareViewResponse_returnsPresignedUrl() {
        FiniteArtifactViewPrepareService service = new FiniteArtifactViewPrepareService(s3ClientService);
        Map<String, String> sinkS3 = Map.of(
                S3Utils.BUCKET_NAME, "consumer-bucket",
                S3Utils.OBJECT_KEY, "tp-1",
                S3Utils.ACCESS_KEY, "bucket-access",
                S3Utils.SECRET_KEY, "bucket-secret",
                S3Utils.REGION, "us-east-1",
                S3Utils.ENDPOINT_OVERRIDE, "http://minio:9000",
                S3Utils.PUBLIC_PRESIGNED_ENDPOINT, "http://downloads.example.com");
        when(s3ClientService.generateGetPresignedUrl(sinkS3, Duration.ofDays(7L)))
                .thenReturn("https://example.test/presigned");

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-1")
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_FIELD_MODE, DataPlaneConstants.METADATA_MODE_VIEW,
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "tp-1",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "bucket-access",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "bucket-secret",
                                        DataPlaneConstants.METADATA_S3_REGION, "us-east-1",
                                        DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, "http://minio:9000",
                                        DataPlaneConstants.METADATA_S3_PUBLIC_PRESIGNED_ENDPOINT,
                                                "http://downloads.example.com"))))
                .build();

        DataFlowPrepareResponse response = service.prepareViewResponse("stream:grpc", message);

        assertThat(response.getProcessId()).isEqualTo("tp-1");
        assertThat(response.getDataAddress())
                .containsEntry(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY, "https://example.test/presigned");
        verify(s3ClientService).generateGetPresignedUrl(sinkS3, Duration.ofDays(7L));
    }

    @Test
    @DisplayName("prepareViewResponse rejects non-finite stream view requests when source.finite=false")
    void prepareViewResponse_rejectsNonFiniteView() {
        FiniteArtifactViewPrepareService service = new FiniteArtifactViewPrepareService(s3ClientService);

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-non-finite")
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SOURCE, Map.of(
                                DataPlaneConstants.METADATA_FIELD_FINITE, "false"),
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_FIELD_MODE, DataPlaneConstants.METADATA_MODE_VIEW,
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "consumer-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "tp-non-finite",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "bucket-access",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "bucket-secret",
                                        DataPlaneConstants.METADATA_S3_REGION, "us-east-1"))))
                .build();

        assertThatThrownBy(() -> service.prepareViewResponse("stream:kafka", message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("VIEW mode is supported only for finite materialized artifacts; non-finite stream view is not implemented for stream:kafka");
    }
}
