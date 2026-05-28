package it.eng.dataplane.api.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import it.eng.dataplane.api.DataPlaneConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DataFlowMessageSchemaContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SCHEMA_RESOURCE_ROOT = "upstream-dps-schemas/";

    @Test
    @DisplayName("Prepare message serializes metadata instead of top-level dataAddress and matches upstream schema")
    void prepareMessageMatchesPinnedSchema() throws Exception {
        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .messageId("message-1")
                .participantId("participant-1")
                .counterPartyId("participant-2")
                .dataspaceContext("dataspace-1")
                .processId("process-1")
                .agreementId("agreement-1")
                .datasetId("dataset-1")
                .transferType("HttpData-PUSH")
                .callbackAddress("https://control-plane.example.com/callbacks/process-1")
                .claims(Map.of("scope", "download"))
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SOURCE,
                        Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3,
                                Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "source-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "dataset-1")),
                        DataPlaneConstants.METADATA_SECTION_SINK,
                        Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3,
                                Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "sink-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "process-1"))))
                .build();

        JsonNode json = MAPPER.valueToTree(message);

        assertThat(json.has("metadata")).isTrue();
        assertThat(json.has("dataAddress")).isFalse();
        assertThat(validate("DataFlowPrepareMessage.schema.json", json)).isEmpty();
    }

    @Test
    @DisplayName("Prepare message no longer exposes legacy dataAddress compatibility methods")
    void prepareMessageDoesNotExposeLegacyDataAddressCompatibilityMethods() {
        assertThat(DataFlowPrepareMessage.class.getMethods())
                .extracting(method -> method.getName())
                .doesNotContain("getDataAddress");
        assertThat(DataFlowPrepareMessage.Builder.class.getMethods())
                .extracting(method -> method.getName())
                .doesNotContain("dataAddress");
    }

    @Test
    @DisplayName("Start message serializes dataAddress and metadata and matches upstream schema")
    void startMessageMatchesPinnedSchema() throws Exception {
        DataAddress dataAddress = DataAddress.Builder.newInstance()
                .endpointType("https://w3id.org/idsa/v4.1/HTTP")
                .endpoint("https://example.com/data")
                .endpointProperties(java.util.List.of(
                        EndpointProperty.Builder.newInstance()
                                .name("authorization")
                                .value("Bearer token")
                                .build()))
                .build();
        DataFlowStartMessage message = DataFlowStartMessage.Builder.newInstance()
                .messageId("message-2")
                .participantId("participant-1")
                .counterPartyId("participant-2")
                .dataspaceContext("dataspace-1")
                .processId("process-2")
                .agreementId("agreement-2")
                .datasetId("dataset-2")
                .transferType("HttpData-PULL")
                .callbackAddress("https://control-plane.example.com/callbacks/process-2")
                .claims(Map.of("scope", "download"))
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SOURCE,
                        Map.of(DataPlaneConstants.METADATA_SECTION_S3,
                                Map.of(DataPlaneConstants.METADATA_S3_BUCKET_NAME, "source-bucket"))))
                .dataAddress(dataAddress)
                .build();

        JsonNode json = MAPPER.valueToTree(message);

        assertThat(json.has("dataAddress")).isTrue();
        assertThat(json.has("metadata")).isTrue();
        assertThat(validate("DataFlowStartMessage.schema.json", json)).isEmpty();
    }

    private Set<ValidationMessage> validate(String schemaFile, JsonNode json) throws Exception {
        URL schemaUrl = DataFlowMessageSchemaContractTest.class
                .getClassLoader()
                .getResource(SCHEMA_RESOURCE_ROOT + schemaFile);
        assertThat(schemaUrl).isNotNull();
        JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schemaUrl.toURI());
        return schema.validate(json);
    }
}
