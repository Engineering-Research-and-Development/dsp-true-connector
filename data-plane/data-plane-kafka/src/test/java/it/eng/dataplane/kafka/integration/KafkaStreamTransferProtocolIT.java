package it.eng.dataplane.kafka.integration;

import it.eng.dataplane.api.DataPlaneConstants;
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
import it.eng.tools.s3.util.S3Utils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
                .metadata(Map.of("source", Map.of("sourceType", "s3")))
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
                .metadata(Map.of("source", Map.of("sourceType", "s3", "finite", "false")))
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
                .metadata(Map.of("source", Map.of("sourceType", "s3")))
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

        ReflectionTestUtils.setField(protocol, "bootstrapServers", EMBEDDED_KAFKA.getBrokersAsString());
        ReflectionTestUtils.setField(protocol, "topicPrefix", "stream-topic-");
        ReflectionTestUtils.setField(protocol, "groupIdPrefix", "stream-group-");
        ReflectionTestUtils.setField(protocol, "sourceReaderRegistry",
                new SourceReaderRegistry(List.of(new FixedSourceReader("hello kafka"))));
        ReflectionTestUtils.setField(protocol, "sinkWriterRegistry",
                new SinkWriterRegistry(List.of(sinkWriter)));
        ReflectionTestUtils.setField(protocol, "controlPlaneClient", controlPlaneClient);
        ReflectionTestUtils.setField(protocol, "transferExecutor", transferExecutor);

        DataFlowPrepareResponse prepareResponse = protocol.prepare(DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-finite-kafka-run")
                .datasetId("dataset-3")
                .callbackAddress("http://control-plane/callback")
                .metadata(Map.of("source", Map.of("sourceType", "s3")))
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

            DataFlowPrepareResponse prepareResponse = protocol.prepare(DataFlowPrepareMessage.Builder.newInstance()
                    .processId("tp-non-finite-kafka-run")
                    .datasetId("dataset-4")
                    .callbackAddress("http://control-plane/callback")
                    .metadata(Map.of("source", Map.of("sourceType", "s3", "finite", "false")))
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
    @DisplayName("interrupted non-finite publisher shutdown does not escape as uncaught async failure")
    void interruptedNonFinitePublisherShutdownDoesNotEscapeAsUncaughtAsyncFailure() throws Exception {
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol();
        BlockingSinkWriter sinkWriter = new BlockingSinkWriter();
        ControlPlaneClient controlPlaneClient = Mockito.mock(ControlPlaneClient.class);
        BlockingQueue<Throwable> uncaughtFailures = new LinkedBlockingQueue<>();
        ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setUncaughtExceptionHandler((ignoredThread, throwable) -> uncaughtFailures.add(throwable));
            return thread;
        });

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

            DataFlowPrepareResponse prepareResponse = protocol.prepare(DataFlowPrepareMessage.Builder.newInstance()
                    .processId("tp-non-finite-kafka-interrupt")
                    .datasetId("dataset-6")
                    .callbackAddress("http://control-plane/callback")
                    .metadata(Map.of("source", Map.of("sourceType", "s3", "finite", "false")))
                    .build());

            DataFlow dataFlow = DataFlow.Builder.newInstance()
                    .dataFlowId("df-non-finite-kafka-interrupt")
                    .processId("tp-non-finite-kafka-interrupt")
                    .datasetId("dataset-6")
                    .callbackAddress("http://control-plane/callback")
                    .transferType(KafkaStreamTransferProtocol.PROTOCOL_ID)
                    .dataAddress(prepareResponse.getDataAddress())
                    .build();

            protocol.initiateTransfer(dataFlow);

            assertTrue(sinkWriter.awaitStarted(10, TimeUnit.SECONDS));
            Thread.sleep(500);
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));

            Throwable failure = uncaughtFailures.poll(1, TimeUnit.SECONDS);
            assertNull(failure, () -> "Unexpected uncaught async failure: " + failure);
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

            DataFlowPrepareResponse prepareResponse = protocol.prepare(DataFlowPrepareMessage.Builder.newInstance()
                    .processId("tp-non-finite-kafka-terminate")
                    .datasetId("dataset-5")
                    .callbackAddress("http://control-plane/callback")
                    .metadata(Map.of("source", Map.of("sourceType", "s3", "finite", "false")))
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

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("prepare uses CP-provided source bucket from metadata when publishing to Kafka")
    void prepare_usesSourceBucketFromPrepareMetadata() throws Exception {
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol();
        ContextCapturingSinkWriter sinkWriter = new ContextCapturingSinkWriter();
        ContextCapturingSourceReader sourceReader = new ContextCapturingSourceReader("payload");
        ControlPlaneClient controlPlaneClient = Mockito.mock(ControlPlaneClient.class);
        Executor transferExecutor = Runnable::run;

        ReflectionTestUtils.setField(protocol, "bootstrapServers", EMBEDDED_KAFKA.getBrokersAsString());
        ReflectionTestUtils.setField(protocol, "topicPrefix", "stream-topic-");
        ReflectionTestUtils.setField(protocol, "groupIdPrefix", "stream-group-");
        ReflectionTestUtils.setField(protocol, "sourceReaderRegistry",
                new SourceReaderRegistry(java.util.List.of(sourceReader)));
        ReflectionTestUtils.setField(protocol, "sinkWriterRegistry",
                new SinkWriterRegistry(java.util.List.of(sinkWriter)));
        ReflectionTestUtils.setField(protocol, "controlPlaneClient", controlPlaneClient);
        ReflectionTestUtils.setField(protocol, "transferExecutor", transferExecutor);

        DataFlowPrepareResponse prepareResponse = protocol.prepare(DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-source-meta-kafka")
                .datasetId("dataset-meta")
                .callbackAddress("http://cp/callback")
                .metadata(Map.of(DataPlaneConstants.METADATA_SECTION_SOURCE, Map.of(
                        DataPlaneConstants.METADATA_FIELD_SOURCE_TYPE, "s3",
                        DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                DataPlaneConstants.METADATA_S3_BUCKET_NAME, "cp-source-bucket",
                                DataPlaneConstants.METADATA_S3_REGION, "eu-west-2",
                                DataPlaneConstants.METADATA_S3_ACCESS_KEY, "cp-src-key",
                                DataPlaneConstants.METADATA_S3_SECRET_KEY, "cp-src-secret")
                )))
                .build());

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-source-meta-kafka")
                .processId("tp-source-meta-kafka")
                .datasetId("dataset-meta")
                .callbackAddress("http://cp/callback")
                .transferType(KafkaStreamTransferProtocol.PROTOCOL_ID)
                .dataAddress(prepareResponse.getDataAddress())
                .build();

        protocol.initiateTransfer(dataFlow).get(30, TimeUnit.SECONDS);

        SourceContext capturedSourceContext = sourceReader.getCapturedContext();
        assertNotNull(capturedSourceContext);
        assertEquals("cp-source-bucket", capturedSourceContext.getProperties().get(S3Utils.BUCKET_NAME));
        assertEquals("dataset-meta", capturedSourceContext.getProperties().get(S3Utils.OBJECT_KEY));
        assertEquals("eu-west-2", capturedSourceContext.getProperties().get(S3Utils.REGION));
        assertEquals("cp-src-key", capturedSourceContext.getProperties().get(S3Utils.ACCESS_KEY));
        assertEquals("cp-src-secret", capturedSourceContext.getProperties().get(S3Utils.SECRET_KEY));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("initiateTransfer uses CP-provided metadata.sink.s3 properties for sink context")
    void initiateTransfer_usesCpProvidedSinkPropertiesFromDataAddress() throws Exception {
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol();
        ContextCapturingSinkWriter sinkWriter = new ContextCapturingSinkWriter();
        ControlPlaneClient controlPlaneClient = Mockito.mock(ControlPlaneClient.class);
        Executor transferExecutor = Runnable::run;

        ReflectionTestUtils.setField(protocol, "bootstrapServers", EMBEDDED_KAFKA.getBrokersAsString());
        ReflectionTestUtils.setField(protocol, "topicPrefix", "stream-topic-");
        ReflectionTestUtils.setField(protocol, "groupIdPrefix", "stream-group-");
        ReflectionTestUtils.setField(protocol, "sourceReaderRegistry",
                new SourceReaderRegistry(java.util.List.of(new FixedSourceReader("data"))));
        ReflectionTestUtils.setField(protocol, "sinkWriterRegistry",
                new SinkWriterRegistry(java.util.List.of(sinkWriter)));
        ReflectionTestUtils.setField(protocol, "controlPlaneClient", controlPlaneClient);
        ReflectionTestUtils.setField(protocol, "transferExecutor", transferExecutor);

        DataFlowPrepareResponse prepareResponse = protocol.prepare(DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-sink-cp-kafka")
                .datasetId("dataset-sink-cp")
                .callbackAddress("http://cp/callback")
                .metadata(Map.of("source", Map.of("sourceType", "s3")))
                .build());

        // S3 sink credentials come from metadata.sink.s3, not flat dataAddress keys
        Map<String, String> dataAddress = new HashMap<>(prepareResponse.getDataAddress());

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-sink-cp-kafka")
                .processId("tp-sink-cp-kafka")
                .datasetId("dataset-sink-cp")
                .callbackAddress("http://cp/callback")
                .transferType(KafkaStreamTransferProtocol.PROTOCOL_ID)
                .dataAddress(dataAddress)
                .metadata(Map.of(
                        DataPlaneConstants.METADATA_SECTION_SINK, Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "cp-sink-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "cp-sink-key",
                                        DataPlaneConstants.METADATA_S3_REGION, "ap-southeast-1",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "cp-sink-access",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "cp-sink-secret"))))
                .build();

        protocol.initiateTransfer(dataFlow).get(30, TimeUnit.SECONDS);

        SinkContext capturedSinkContext = sinkWriter.getCapturedContext();
        assertNotNull(capturedSinkContext);
        assertEquals("cp-sink-bucket", capturedSinkContext.getProperties().get(S3Utils.BUCKET_NAME));
        assertEquals("cp-sink-key", capturedSinkContext.getProperties().get(S3Utils.OBJECT_KEY));
        assertEquals("ap-southeast-1", capturedSinkContext.getProperties().get(S3Utils.REGION));
        assertEquals("cp-sink-access", capturedSinkContext.getProperties().get(S3Utils.ACCESS_KEY));
        assertEquals("cp-sink-secret", capturedSinkContext.getProperties().get(S3Utils.SECRET_KEY));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("thrown sendCompleted callback exception does not fail a successful transfer")
    void sendCompletedCallbackExceptionDoesNotFailTransfer() throws Exception {
        KafkaStreamTransferProtocol protocol = new KafkaStreamTransferProtocol();
        RecordingSinkWriter sinkWriter = new RecordingSinkWriter();
        ControlPlaneClient controlPlaneClient = Mockito.mock(ControlPlaneClient.class);
        Executor transferExecutor = Runnable::run;

        // Mock sendCompleted to throw an exception
        Mockito.doThrow(new RuntimeException("Callback delivery failed"))
                .when(controlPlaneClient)
                .sendCompleted(anyString(), anyString(), anyMap());

        ReflectionTestUtils.setField(protocol, "bootstrapServers", EMBEDDED_KAFKA.getBrokersAsString());
        ReflectionTestUtils.setField(protocol, "topicPrefix", "stream-topic-");
        ReflectionTestUtils.setField(protocol, "groupIdPrefix", "stream-group-");
        ReflectionTestUtils.setField(protocol, "sourceReaderRegistry",
                new SourceReaderRegistry(List.of(new FixedSourceReader("payload from callback test"))));
        ReflectionTestUtils.setField(protocol, "sinkWriterRegistry",
                new SinkWriterRegistry(List.of(sinkWriter)));
        ReflectionTestUtils.setField(protocol, "controlPlaneClient", controlPlaneClient);
        ReflectionTestUtils.setField(protocol, "transferExecutor", transferExecutor);

        DataFlowPrepareResponse prepareResponse = protocol.prepare(DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-callback-exception")
                .datasetId("dataset-callback-exception")
                .callbackAddress("http://control-plane/callback")
                .metadata(Map.of("source", Map.of("sourceType", "s3")))
                .build());

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-callback-exception")
                .processId("tp-callback-exception")
                .datasetId("dataset-callback-exception")
                .callbackAddress("http://control-plane/callback")
                .transferType(KafkaStreamTransferProtocol.PROTOCOL_ID)
                .dataAddress(prepareResponse.getDataAddress())
                .build();

        CompletableFuture<DataFlowResult> resultFuture = protocol.initiateTransfer(dataFlow);
        DataFlowResult result = resultFuture.get(30, TimeUnit.SECONDS);

        // Transfer should succeed even though sendCompleted threw an exception
        assertTrue(result.isSuccess(), "Transfer should succeed despite sendCompleted callback failure");
        assertEquals("payload from callback test", sinkWriter.getContent());
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
     * Source reader that records the SourceContext it was opened with.
     */
    private static final class ContextCapturingSourceReader implements SourceReader {

        private final byte[] payload;
        private volatile SourceContext capturedContext;

        private ContextCapturingSourceReader(String payload) {
            this.payload = payload.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getSourceType() {
            return "s3";
        }

        @Override
        public SourceOpenResult open(SourceContext context) {
            this.capturedContext = context;
            return SourceOpenResult.success(new ByteArrayInputStream(payload),
                    "application/octet-stream", (long) payload.length, true);
        }

        private SourceContext getCapturedContext() {
            return capturedContext;
        }
    }

    /**
     * Sink writer that records the SinkContext it was invoked with.
     */
    private static final class ContextCapturingSinkWriter implements SinkWriter {

        private volatile SinkContext capturedContext;

        @Override
        public String getSinkType() {
            return "s3";
        }

        @Override
        public SinkWriteResult write(InputStream data, SinkContext context) {
            this.capturedContext = context;
            try {
                data.readAllBytes();
                return SinkWriteResult.success("ok");
            } catch (IOException exception) {
                return SinkWriteResult.failure(exception.getMessage());
            }
        }

        private SinkContext getCapturedContext() {
            return capturedContext;
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
}
