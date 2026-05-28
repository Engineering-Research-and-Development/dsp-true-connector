package it.eng.dataplane.kafka.integration;

import it.eng.dataplane.api.io.SinkContext;
import it.eng.dataplane.api.io.SinkWriteResult;
import it.eng.dataplane.api.io.SinkWriter;
import it.eng.dataplane.api.io.SourceContext;
import it.eng.dataplane.api.io.SourceOpenResult;
import it.eng.dataplane.api.io.SourceReader;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.core.registry.SinkWriterRegistry;
import it.eng.dataplane.core.registry.SourceReaderRegistry;
import it.eng.dataplane.kafka.KafkaStreamTransferProtocol;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for {@link KafkaStreamTransferProtocol}.
 */
class KafkaStreamTransferProtocolIT {

    private static final EmbeddedKafkaBroker EMBEDDED_KAFKA = new EmbeddedKafkaKraftBroker(1, 1);

    static {
        EMBEDDED_KAFKA.afterPropertiesSet();
    }

    @AfterAll
    static void destroyBroker() {
        EMBEDDED_KAFKA.destroy();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("finite Kafka flow streams source bytes into the sink and completes")
    void finiteKafkaFlowStreamsIntoSink() throws Exception {
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol();
        IntegrationRecordingSinkWriter sinkWriter = new IntegrationRecordingSinkWriter();
        ControlPlaneClient controlPlaneClient = Mockito.mock(ControlPlaneClient.class);

        ReflectionTestUtils.setField(protocol, "bootstrapServers", EMBEDDED_KAFKA.getBrokersAsString());
        ReflectionTestUtils.setField(protocol, "topicPrefix", "stream-topic-");
        ReflectionTestUtils.setField(protocol, "groupIdPrefix", "stream-group-");
        ReflectionTestUtils.setField(protocol, "sourceReaderRegistry",
                new SourceReaderRegistry(java.util.List.of(new IntegrationSourceReader("hello kafka it"))));
        ReflectionTestUtils.setField(protocol, "sinkWriterRegistry",
                new SinkWriterRegistry(java.util.List.of(sinkWriter)));
        ReflectionTestUtils.setField(protocol, "controlPlaneClient", controlPlaneClient);
        ReflectionTestUtils.setField(protocol, "transferExecutor", (Executor) Runnable::run);

        DataFlowPrepareResponse prepareResponse = protocol.prepare(DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-finite-kafka-it")
                .datasetId("dataset-it-1")
                .callbackAddress("http://control-plane/callback")
                .metadata(Map.of("source", Map.of("sourceType", "s3")))
                .build());

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-finite-kafka-it")
                .processId("tp-finite-kafka-it")
                .datasetId("dataset-it-1")
                .callbackAddress("http://control-plane/callback")
                .transferType(KafkaStreamTransferProtocol.PROTOCOL_ID)
                .dataAddress(prepareResponse.getDataAddress())
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).get(30, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("hello kafka it", sinkWriter.getContent());
        verify(controlPlaneClient).sendCompleted(eq("http://control-plane/callback"),
                eq("tp-finite-kafka-it"), anyMap());
    }

    /**
     * Finite source reader used by the integration test.
     */
    private static final class IntegrationSourceReader implements SourceReader {

        private final byte[] payload;

        private IntegrationSourceReader(String payload) {
            this.payload = payload.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getSourceType() {
            return "s3";
        }

        @Override
        public SourceOpenResult open(SourceContext context) {
            return SourceOpenResult.success(new ByteArrayInputStream(payload),
                    "application/octet-stream", (long) payload.length, true);
        }
    }

    /**
     * Recording sink writer used by the integration test.
     */
    private static final class IntegrationRecordingSinkWriter implements SinkWriter {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public String getSinkType() {
            return "s3";
        }

        @Override
        public SinkWriteResult write(InputStream data, SinkContext context) {
            try {
                buffer.write(data.readAllBytes());
                return SinkWriteResult.success("ok");
            } catch (IOException exception) {
                return SinkWriteResult.failure(exception.getMessage());
            }
        }

        private String getContent() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
