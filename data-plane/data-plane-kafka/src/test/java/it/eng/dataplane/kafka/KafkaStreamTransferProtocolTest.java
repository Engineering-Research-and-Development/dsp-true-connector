package it.eng.dataplane.kafka;

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
import it.eng.tools.s3.properties.S3Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterAll;
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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link KafkaStreamTransferProtocol}.
 */
class KafkaStreamTransferProtocolTest {

    private static final EmbeddedKafkaBroker EMBEDDED_KAFKA = new EmbeddedKafkaKraftBroker(1, 1);

    static {
        EMBEDDED_KAFKA.afterPropertiesSet();
    }

    @AfterAll
    static void destroyBroker() {
        EMBEDDED_KAFKA.destroy();
    }

    @Test
    @DisplayName("prepare returns Kafka endpoint metadata for a finite session")
    void prepareFiniteSessionReturnsKafkaMetadata() {
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol();
        ReflectionTestUtils.setField(protocol, "bootstrapServers", "kafka:9092");
        ReflectionTestUtils.setField(protocol, "topicPrefix", "stream-topic-");
        ReflectionTestUtils.setField(protocol, "groupIdPrefix", "stream-group-");

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-finite-kafka")
                .datasetId("dataset-1")
                .dataAddress(Map.of("sourceType", "s3"))
                .build();

        DataFlowPrepareResponse response = protocol.prepare(message);

        assertEquals("tp-finite-kafka", response.getProcessId());
        assertNotNull(response.getDataAddress());
        assertEquals("kafka", response.getDataAddress().get("endpointType"));
        assertEquals("kafka:9092", response.getDataAddress().get("bootstrapServers"));
        assertEquals("stream-topic-tp-finite-kafka", response.getDataAddress().get("topic"));
        assertEquals("stream-group-tp-finite-kafka", response.getDataAddress().get("groupId"));
        assertEquals("finite", response.getDataAddress().get("mode"));
    }

    @Test
    @DisplayName("prepare returns non-finite mode when finite hint is false")
    void prepareNonFiniteSessionReturnsNonFiniteMode() {
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol();
        ReflectionTestUtils.setField(protocol, "bootstrapServers", "kafka:9092");
        ReflectionTestUtils.setField(protocol, "topicPrefix", "stream-topic-");
        ReflectionTestUtils.setField(protocol, "groupIdPrefix", "stream-group-");

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-non-finite-kafka")
                .datasetId("dataset-2")
                .dataAddress(Map.of("sourceType", "s3", "finite", "false"))
                .build();

        DataFlowPrepareResponse response = protocol.prepare(message);

        assertEquals("non-finite", response.getDataAddress().get("mode"));
    }

    @Test
    @DisplayName("prepare normalizes Kafka topic names derived from URN process IDs")
    void prepareNormalizesKafkaTopicNameFromUrnProcessId() {
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol();
        ReflectionTestUtils.setField(protocol, "bootstrapServers", "kafka:9092");
        ReflectionTestUtils.setField(protocol, "topicPrefix", "stream-topic-");
        ReflectionTestUtils.setField(protocol, "groupIdPrefix", "stream-group-");

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("urn:uuid:abc45798-1434-4932-8baf-ab7fd66ac4d5")
                .datasetId("dataset-urn")
                .dataAddress(Map.of("sourceType", "s3"))
                .build();

        DataFlowPrepareResponse response = protocol.prepare(message);

        assertEquals("stream-topic-urn_uuid_abc45798-1434-4932-8baf-ab7fd66ac4d5",
                response.getDataAddress().get("topic"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("prepare and initiateTransfer stream a finite payload through Kafka into the sink")
    void prepareAndInitiateTransferFinitePayloadWritesToSink() throws Exception {
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol();
        RecordingSinkWriter sinkWriter = new RecordingSinkWriter();
        ControlPlaneClient controlPlaneClient = Mockito.mock(ControlPlaneClient.class);
        Executor transferExecutor = Runnable::run;
        S3Properties s3Properties = new S3Properties();
        s3Properties.setBucketName("bucket-a");
        s3Properties.setRegion("us-east-1");
        s3Properties.setEndpoint("http://minio:9000");
        s3Properties.setAccessKey("access-key");
        s3Properties.setSecretKey("secret-key");

        ReflectionTestUtils.setField(protocol, "bootstrapServers", EMBEDDED_KAFKA.getBrokersAsString());
        ReflectionTestUtils.setField(protocol, "topicPrefix", "stream-topic-");
        ReflectionTestUtils.setField(protocol, "groupIdPrefix", "stream-group-");
        ReflectionTestUtils.setField(protocol, "sourceReaderRegistry",
                new SourceReaderRegistry(java.util.List.of(new FixedSourceReader("hello kafka"))));
        ReflectionTestUtils.setField(protocol, "sinkWriterRegistry",
                new SinkWriterRegistry(java.util.List.of(sinkWriter)));
        ReflectionTestUtils.setField(protocol, "controlPlaneClient", controlPlaneClient);
        ReflectionTestUtils.setField(protocol, "transferExecutor", transferExecutor);
        ReflectionTestUtils.setField(protocol, "s3Properties", s3Properties);

        DataFlowPrepareResponse prepareResponse = protocol.prepare(DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-finite-kafka-run")
                .datasetId("dataset-3")
                .callbackAddress("http://control-plane/callback")
                .dataAddress(Map.of("sourceType", "s3"))
                .build());

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-finite-kafka-run")
                .processId("tp-finite-kafka-run")
                .datasetId("dataset-3")
                .callbackAddress("http://control-plane/callback")
                .transferType(KafkaStreamTransferProtocol.PROTOCOL_ID)
                .dataAddress(prepareResponse.getDataAddress())
                .build();

        CompletableFuture<DataFlowResult> resultFuture = protocol.initiateTransfer(dataFlow);
        DataFlowResult result = resultFuture.get(30, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("hello kafka", sinkWriter.getContent());
        verify(controlPlaneClient).sendCompleted(eq("http://control-plane/callback"),
                eq("tp-finite-kafka-run"), anyMap());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("initiateTransfer keeps non-finite streams open and does not send completed")
    void initiateTransferNonFiniteStreamDoesNotAutoComplete() throws Exception {
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol();
        BlockingSinkWriter sinkWriter = new BlockingSinkWriter();
        ControlPlaneClient controlPlaneClient = Mockito.mock(ControlPlaneClient.class);
        ExecutorService executorService = Executors.newCachedThreadPool();
        S3Properties s3Properties = new S3Properties();
        s3Properties.setBucketName("bucket-a");
        s3Properties.setRegion("us-east-1");
        s3Properties.setEndpoint("http://minio:9000");
        s3Properties.setAccessKey("access-key");
        s3Properties.setSecretKey("secret-key");

        try {
            ReflectionTestUtils.setField(protocol, "bootstrapServers", EMBEDDED_KAFKA.getBrokersAsString());
            ReflectionTestUtils.setField(protocol, "topicPrefix", "stream-topic-");
            ReflectionTestUtils.setField(protocol, "groupIdPrefix", "stream-group-");
            ReflectionTestUtils.setField(protocol, "sourceReaderRegistry",
                    new SourceReaderRegistry(java.util.List.of(new NonFiniteSourceReader("live"))));
            ReflectionTestUtils.setField(protocol, "sinkWriterRegistry",
                    new SinkWriterRegistry(java.util.List.of(sinkWriter)));
            ReflectionTestUtils.setField(protocol, "controlPlaneClient", controlPlaneClient);
            ReflectionTestUtils.setField(protocol, "transferExecutor", executorService);
            ReflectionTestUtils.setField(protocol, "s3Properties", s3Properties);

            DataFlowPrepareResponse prepareResponse = protocol.prepare(DataFlowPrepareMessage.Builder.newInstance()
                    .processId("tp-non-finite-kafka-run")
                    .datasetId("dataset-4")
                    .callbackAddress("http://control-plane/callback")
                    .dataAddress(Map.of("sourceType", "s3", "finite", "false"))
                    .build());

            DataFlow dataFlow = DataFlow.Builder.newInstance()
                    .dataFlowId("df-non-finite-kafka-run")
                    .processId("tp-non-finite-kafka-run")
                    .datasetId("dataset-4")
                    .callbackAddress("http://control-plane/callback")
                    .transferType(KafkaStreamTransferProtocol.PROTOCOL_ID)
                    .dataAddress(prepareResponse.getDataAddress())
                    .build();

            CompletableFuture<DataFlowResult> resultFuture = protocol.initiateTransfer(dataFlow);

            assertTrue(sinkWriter.awaitStarted(10, TimeUnit.SECONDS));
            Thread.sleep(500);
            assertFalse(resultFuture.isDone());
            verify(controlPlaneClient, never()).sendCompleted(eq("http://control-plane/callback"),
                    eq("tp-non-finite-kafka-run"), anyMap());
        } finally {
            sinkWriter.release();
            executorService.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("terminateTransfer stops a non-finite Kafka stream and resolves the running future")
    void terminateTransferStopsNonFiniteStream() throws Exception {
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol();
        TerminableSinkWriter sinkWriter = new TerminableSinkWriter();
        ControlPlaneClient controlPlaneClient = Mockito.mock(ControlPlaneClient.class);
        ExecutorService executorService = Executors.newCachedThreadPool();
        S3Properties s3Properties = new S3Properties();
        s3Properties.setBucketName("bucket-a");
        s3Properties.setRegion("us-east-1");
        s3Properties.setEndpoint("http://minio:9000");
        s3Properties.setAccessKey("access-key");
        s3Properties.setSecretKey("secret-key");

        try {
            ReflectionTestUtils.setField(protocol, "bootstrapServers", EMBEDDED_KAFKA.getBrokersAsString());
            ReflectionTestUtils.setField(protocol, "topicPrefix", "stream-topic-");
            ReflectionTestUtils.setField(protocol, "groupIdPrefix", "stream-group-");
            ReflectionTestUtils.setField(protocol, "sourceReaderRegistry",
                    new SourceReaderRegistry(java.util.List.of(new NonFiniteSourceReader("live"))));
            ReflectionTestUtils.setField(protocol, "sinkWriterRegistry",
                    new SinkWriterRegistry(java.util.List.of(sinkWriter)));
            ReflectionTestUtils.setField(protocol, "controlPlaneClient", controlPlaneClient);
            ReflectionTestUtils.setField(protocol, "transferExecutor", executorService);
            ReflectionTestUtils.setField(protocol, "s3Properties", s3Properties);

            DataFlowPrepareResponse prepareResponse = protocol.prepare(DataFlowPrepareMessage.Builder.newInstance()
                    .processId("tp-non-finite-kafka-terminate")
                    .datasetId("dataset-5")
                    .callbackAddress("http://control-plane/callback")
                    .dataAddress(Map.of("sourceType", "s3", "finite", "false"))
                    .build());

            DataFlow dataFlow = DataFlow.Builder.newInstance()
                    .dataFlowId("df-non-finite-kafka-terminate")
                    .processId("tp-non-finite-kafka-terminate")
                    .datasetId("dataset-5")
                    .callbackAddress("http://control-plane/callback")
                    .transferType(KafkaStreamTransferProtocol.PROTOCOL_ID)
                    .dataAddress(prepareResponse.getDataAddress())
                    .build();

            CompletableFuture<DataFlowResult> resultFuture = protocol.initiateTransfer(dataFlow);

            assertTrue(sinkWriter.awaitStarted(10, TimeUnit.SECONDS));

            DataFlowResult terminateResult = protocol.terminateTransfer("df-non-finite-kafka-terminate")
                    .get(5, TimeUnit.SECONDS);
            assertTrue(terminateResult.isSuccess());

            DataFlowResult result = resultFuture.get(5, TimeUnit.SECONDS);
            assertFalse(result.isSuccess());
            assertEquals("transfer terminated", result.getErrorMessage());
            verify(controlPlaneClient, never()).sendCompleted(eq("http://control-plane/callback"),
                    eq("tp-non-finite-kafka-terminate"), anyMap());
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * Fixed source reader for deterministic finite test payloads.
     */
    private static final class FixedSourceReader implements SourceReader {

        private final byte[] payload;

        private FixedSourceReader(String payload) {
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
     * Source reader that emits initial bytes and then stays open.
     */
    private static final class NonFiniteSourceReader implements SourceReader {

        private final byte[] initialPayload;

        private NonFiniteSourceReader(String initialPayload) {
            this.initialPayload = initialPayload.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getSourceType() {
            return "s3";
        }

        @Override
        public SourceOpenResult open(SourceContext context) {
            try {
                PipedInputStream inputStream = new PipedInputStream();
                PipedOutputStream outputStream = new PipedOutputStream(inputStream);
                Thread producerThread = new Thread(() -> {
                    try {
                        outputStream.write(initialPayload);
                        outputStream.flush();
                        while (!Thread.currentThread().isInterrupted()) {
                            Thread.sleep(100);
                        }
                    } catch (IOException | InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        try {
                            outputStream.close();
                        } catch (IOException ignored) {
                            // test helper close path
                        }
                    }
                });
                producerThread.setDaemon(true);
                producerThread.start();
                return SourceOpenResult.success(inputStream, "application/octet-stream", null, false);
            } catch (IOException exception) {
                return SourceOpenResult.failure(exception.getMessage());
            }
        }
    }

    /**
     * Recording sink writer used to verify consumed Kafka data.
     */
    private static final class RecordingSinkWriter implements SinkWriter {

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

    /**
     * Sink writer that confirms data consumption has started and then blocks until released.
     */
    private static final class BlockingSinkWriter implements SinkWriter {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public String getSinkType() {
            return "s3";
        }

        @Override
        public SinkWriteResult write(InputStream data, SinkContext context) {
            try {
                started.countDown();
                release.await(10, TimeUnit.SECONDS);
                return SinkWriteResult.success("ok");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return SinkWriteResult.failure(exception.getMessage());
            }
        }

        private boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
            return started.await(timeout, unit);
        }

        private void release() {
            release.countDown();
        }
    }

    /**
     * Sink writer that stays active until the input stream is closed by termination.
     */
    private static final class TerminableSinkWriter implements SinkWriter {

        private final CountDownLatch started = new CountDownLatch(1);

        @Override
        public String getSinkType() {
            return "s3";
        }

        @Override
        public SinkWriteResult write(InputStream data, SinkContext context) {
            started.countDown();
            try {
                while (data.read() >= 0) {
                    // keep reading until termination closes the stream
                }
                return SinkWriteResult.success("closed");
            } catch (IOException exception) {
                return SinkWriteResult.failure(exception.getMessage());
            }
        }

        private boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
            return started.await(timeout, unit);
        }
    }
}
