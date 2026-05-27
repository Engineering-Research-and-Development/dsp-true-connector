package it.eng.dataplane.kafka;

import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.io.SinkContext;
import it.eng.dataplane.api.io.SinkWriteResult;
import it.eng.dataplane.api.io.SinkWriter;
import it.eng.dataplane.api.io.SourceContext;
import it.eng.dataplane.api.io.SourceOpenResult;
import it.eng.dataplane.api.io.SourceReader;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.core.registry.SinkWriterRegistry;
import it.eng.dataplane.core.registry.SourceReaderRegistry;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.util.S3Utils;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka-backed transfer protocol implementation.
 */
@Component
public class KafkaStreamTransferProtocol implements DataTransferProtocol {

    private static final String DEFAULT_SOURCE_TYPE = "s3";
    private static final String DEFAULT_SINK_TYPE = "s3";
    private static final String CHUNK_TYPE_HEADER = "chunkType";
    private static final String CHUNK_TYPE_DATA = "data";
    private static final String CHUNK_TYPE_EOF = "eof";

    static final String ENDPOINT_TYPE_KEY = "endpointType";
    static final String BOOTSTRAP_SERVERS_KEY = "bootstrapServers";
    static final String TOPIC_KEY = "topic";
    static final String GROUP_ID_KEY = "groupId";
    static final String MODE_KEY = "mode";
    static final String FINITE_KEY = "finite";
    static final String MODE_FINITE = "finite";
    static final String MODE_NON_FINITE = "non-finite";
    private static final String TERMINATED_MESSAGE = "transfer terminated";

    /**
     * Kafka streaming transport profile identifier.
     */
    public static final String PROTOCOL_ID = "stream:kafka";

    @Value("${dataplane.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${dataplane.kafka.topic-prefix:stream-topic-}")
    private String topicPrefix;

    @Value("${dataplane.kafka.group-id-prefix:stream-group-}")
    private String groupIdPrefix;

    @Autowired
    private SourceReaderRegistry sourceReaderRegistry;

    @Autowired
    private SinkWriterRegistry sinkWriterRegistry;

    @Autowired
    private ControlPlaneClient controlPlaneClient;

    @Autowired
    private S3Properties s3Properties;

    @Autowired
    @Qualifier("transferExecutor")
    private Executor transferExecutor;

    private final ConcurrentHashMap<String, ActiveKafkaTransfer> activeTransfers = new ConcurrentHashMap<>();

    /**
     * Returns the unique identifier for this transfer protocol.
     *
     * @return {@value PROTOCOL_ID}
     */
    @Override
    public String getProtocolId() {
        return PROTOCOL_ID;
    }

    /**
     * Allocates Kafka transport metadata for a transfer process.
     *
     * @param message the prepare message from the Control Plane
     * @return response containing Kafka endpoint metadata
     */
    @Override
    public DataFlowPrepareResponse prepare(DataFlowPrepareMessage message) {
        Map<String, String> requestDataAddress = message.getDataAddress() == null ? Map.of() : message.getDataAddress();
        boolean finite = !"false".equalsIgnoreCase(requestDataAddress.get(FINITE_KEY));
        String topic = topicPrefix + toKafkaTopicSegment(message.getProcessId());
        String groupId = groupIdPrefix + message.getProcessId();

        Map<String, String> responseDataAddress = new LinkedHashMap<>();
        responseDataAddress.put(ENDPOINT_TYPE_KEY, "kafka");
        responseDataAddress.put(BOOTSTRAP_SERVERS_KEY, bootstrapServers);
        responseDataAddress.put(TOPIC_KEY, topic);
        responseDataAddress.put(GROUP_ID_KEY, groupId);
        responseDataAddress.put(MODE_KEY, finite ? MODE_FINITE : MODE_NON_FINITE);

        if (sourceReaderRegistry != null) {
            createTopic(topic);
            runAsync(() -> publishSourceStream(message, requestDataAddress, topic));
        }

        return DataFlowPrepareResponse.Builder.newInstance()
                .processId(message.getProcessId())
                .dataAddress(responseDataAddress)
                .build();
    }

    /**
     * Initiates a Kafka-backed data transfer.
     *
     * @param dataFlow the data flow to initiate
     * @return a failure result until the Kafka transport is implemented
     */
    @Override
    public CompletableFuture<DataFlowResult> initiateTransfer(DataFlow dataFlow) {
        Map<String, String> dataAddress = dataFlow.getDataAddress();
        if (dataAddress == null) {
            return CompletableFuture.completedFuture(DataFlowResult.failure("dataAddress is required for stream:kafka"));
        }

        String topic = dataAddress.get(TOPIC_KEY);
        String groupId = dataAddress.get(GROUP_ID_KEY);
        String mode = dataAddress.getOrDefault(MODE_KEY, MODE_FINITE);
        if (isBlank(topic) || isBlank(groupId)) {
            return CompletableFuture.completedFuture(
                    DataFlowResult.failure("Missing transport metadata: topic and groupId required for stream:kafka"));
        }

        Optional<SinkWriter> sinkWriterOptional = sinkWriterRegistry == null
                ? Optional.empty()
                : sinkWriterRegistry.getWriter(DEFAULT_SINK_TYPE);
        if (sinkWriterOptional.isEmpty()) {
            return CompletableFuture.completedFuture(
                    DataFlowResult.failure("No SinkWriter available for type: " + DEFAULT_SINK_TYPE));
        }

        CompletableFuture<DataFlowResult> future = new CompletableFuture<>();
        ActiveKafkaTransfer activeTransfer = new ActiveKafkaTransfer(future);
        activeTransfers.put(dataFlow.getDataFlowId(), activeTransfer);
        runAsync(() -> consumeTopicIntoSink(dataFlow, topic, groupId, MODE_FINITE.equals(mode),
                sinkWriterOptional.get(), activeTransfer));
        return future;
    }

    /**
     * Suspends an active Kafka-backed data transfer.
     *
     * @param dataFlowId the ID of the data flow to suspend
     * @return a failure result until the Kafka transport is implemented
     */
    @Override
    public CompletableFuture<DataFlowResult> suspendTransfer(String dataFlowId) {
        return CompletableFuture.completedFuture(DataFlowResult.failure("suspend not implemented for stream:kafka"));
    }

    /**
     * Resumes a suspended Kafka-backed data transfer.
     *
     * @param dataFlowId the ID of the data flow to resume
     * @return a failure result until the Kafka transport is implemented
     */
    @Override
    public CompletableFuture<DataFlowResult> resumeTransfer(String dataFlowId) {
        return CompletableFuture.completedFuture(DataFlowResult.failure("resume not implemented for stream:kafka"));
    }

    /**
     * Terminates a Kafka-backed data transfer.
     *
     * @param dataFlowId the ID of the data flow to terminate
     * @return a success result until active stream lifecycle is implemented
     */
    @Override
    public CompletableFuture<DataFlowResult> terminateTransfer(String dataFlowId) {
        ActiveKafkaTransfer activeTransfer = activeTransfers.remove(dataFlowId);
        if (activeTransfer != null) {
            activeTransfer.terminate();
        }
        return CompletableFuture.completedFuture(DataFlowResult.success());
    }

    private void publishSourceStream(DataFlowPrepareMessage message, Map<String, String> dataAddress, String topic) {
        String sourceType = dataAddress.getOrDefault("sourceType", DEFAULT_SOURCE_TYPE);
        SourceReader sourceReader = sourceReaderRegistry.getReader(sourceType)
                .orElseThrow(() -> new IllegalArgumentException("No SourceReader available for sourceType: " + sourceType));
        SourceContext sourceContext = SourceContext.Builder.newInstance()
                .properties(buildSourceProperties(message.getDatasetId()))
                .build();

        try (SourceOpenResult sourceOpenResult = sourceReader.open(sourceContext)) {
            if (!sourceOpenResult.isSuccess()) {
                throw new IllegalStateException(sourceOpenResult.getErrorMessage());
            }
            publishInputStream(topic, message.getProcessId(), sourceOpenResult.getStream());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to close source stream", exception);
        }
    }

    private void publishInputStream(String topic, String processId, InputStream inputStream) {
        Properties producerProperties = new Properties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(producerProperties)) {
            byte[] chunkBuffer = new byte[8192];
            while (true) {
                int bytesRead = inputStream.read(chunkBuffer);
                if (bytesRead < 0) {
                    break;
                }
                if (bytesRead == 0) {
                    continue;
                }
                byte[] payload = java.util.Arrays.copyOf(chunkBuffer, bytesRead);
                ProducerRecord<String, byte[]> dataRecord = new ProducerRecord<>(topic, processId, payload);
                dataRecord.headers().add(CHUNK_TYPE_HEADER, CHUNK_TYPE_DATA.getBytes());
                RecordMetadata ignored = producer.send(dataRecord).get();
            }
            ProducerRecord<String, byte[]> eofRecord = new ProducerRecord<>(topic, processId, new byte[0]);
            eofRecord.headers().add(CHUNK_TYPE_HEADER, CHUNK_TYPE_EOF.getBytes());
            producer.send(eofRecord).get();
            producer.flush();
        } catch (Exception exception) {
            if (isInterruptedCancellation(exception)) {
                Thread.currentThread().interrupt();
                return;
            }
            throw new IllegalStateException("Failed to publish Kafka stream for process " + processId, exception);
        }
    }

    private boolean isInterruptedCancellation(Throwable throwable) {
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException
                    || current instanceof InterruptedIOException
                    || current instanceof CancellationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void consumeTopicIntoSink(DataFlow dataFlow,
                                      String topic,
                                      String groupId,
                                      boolean finite,
                                      SinkWriter sinkWriter,
                                      ActiveKafkaTransfer activeTransfer) {
        Properties consumerProperties = new Properties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProperties);
             KafkaTopicInputStream kafkaInputStream = new KafkaTopicInputStream(consumer, topic)) {
            activeTransfer.attach(kafkaInputStream);
            SinkWriteResult writeResult = sinkWriter.write(kafkaInputStream, buildSinkContext(dataFlow));
            if (!writeResult.isSuccess()) {
                activeTransfer.complete(DataFlowResult.failure(writeResult.getErrorMessage()));
                return;
            }
            if (finite) {
                sendCompletedSafely(dataFlow.getCallbackAddress(), dataFlow.getProcessId(), dataFlow.getDataAddress());
                activeTransfer.complete(DataFlowResult.success());
                return;
            }
            if (activeTransfer.isTerminated()) {
                activeTransfer.complete(DataFlowResult.failure(TERMINATED_MESSAGE));
                return;
            }
            activeTransfer.complete(DataFlowResult.failure("server closed non-finite stream unexpectedly"));
        } catch (Exception exception) {
            if (activeTransfer.isTerminated()) {
                activeTransfer.complete(DataFlowResult.failure(TERMINATED_MESSAGE));
            } else {
                activeTransfer.complete(DataFlowResult.failure(exception.getMessage()));
            }
        } finally {
            activeTransfers.remove(dataFlow.getDataFlowId(), activeTransfer);
        }
    }

    private void createTopic(String topic) {
        Properties adminProperties = new Properties();
        adminProperties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(adminProperties)) {
            adminClient.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        } catch (Exception exception) {
            if (!exception.getMessage().contains("TopicExistsException")) {
                throw new IllegalStateException("Failed to create Kafka topic " + topic, exception);
            }
        }
    }

    private Map<String, String> buildSourceProperties(String datasetId) {
        if (s3Properties == null) {
            return Map.of(S3Utils.OBJECT_KEY, datasetId);
        }
        return Map.of(
                S3Utils.BUCKET_NAME, s3Properties.getBucketName(),
                S3Utils.OBJECT_KEY, datasetId
        );
    }

    private SinkContext buildSinkContext(DataFlow dataFlow) {
        if (s3Properties == null) {
            return SinkContext.Builder.newInstance()
                    .properties(Map.of(S3Utils.OBJECT_KEY, dataFlow.getProcessId()))
                    .build();
        }

        Map<String, String> sinkProperties = new LinkedHashMap<>();
        sinkProperties.put(S3Utils.BUCKET_NAME, s3Properties.getBucketName());
        sinkProperties.put(S3Utils.OBJECT_KEY, dataFlow.getProcessId());
        sinkProperties.put(S3Utils.REGION, s3Properties.getRegion());
        sinkProperties.put(S3Utils.ENDPOINT_OVERRIDE, s3Properties.getEndpoint());
        sinkProperties.put(S3Utils.ACCESS_KEY, s3Properties.getAccessKey());
        sinkProperties.put(S3Utils.SECRET_KEY, s3Properties.getSecretKey());
        return SinkContext.Builder.newInstance()
                .properties(sinkProperties)
                .build();
    }

    private void sendCompletedSafely(String callbackAddress, String processId, Map<String, String> dataAddress) {
        if (controlPlaneClient != null) {
            controlPlaneClient.sendCompleted(callbackAddress, processId, dataAddress);
        }
    }

    private void runAsync(Runnable runnable) {
        if (transferExecutor != null) {
            transferExecutor.execute(runnable);
            return;
        }
        runnable.run();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String toKafkaTopicSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static final class KafkaTopicInputStream extends InputStream {

        private final KafkaConsumer<String, byte[]> consumer;
        private ByteArrayInputStream currentBuffer = new ByteArrayInputStream(new byte[0]);
        private Iterator<ConsumerRecord<String, byte[]>> recordIterator = List.<ConsumerRecord<String, byte[]>>of().iterator();
        private boolean endOfStream;
        private volatile boolean closed;

        private KafkaTopicInputStream(KafkaConsumer<String, byte[]> consumer, String topic) {
            this.consumer = consumer;
            this.consumer.subscribe(Collections.singleton(topic));
        }

        @Override
        public int read() {
            if (ensureBuffer()) {
                return currentBuffer.read();
            }
            return -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (!ensureBuffer()) {
                return -1;
            }
            return currentBuffer.read(b, off, len);
        }

        @Override
        public void close() {
            closed = true;
            consumer.close();
        }

        private boolean ensureBuffer() {
            while (!endOfStream) {
                if (closed) {
                    return false;
                }
                if (currentBuffer.available() > 0) {
                    return true;
                }
                if (!recordIterator.hasNext()) {
                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));
                    recordIterator = records.iterator();
                    if (!recordIterator.hasNext()) {
                        continue;
                    }
                }
                ConsumerRecord<String, byte[]> record = recordIterator.next();
                String chunkType = headerValue(record.headers().lastHeader(CHUNK_TYPE_HEADER));
                if (CHUNK_TYPE_EOF.equals(chunkType)) {
                    endOfStream = true;
                    return false;
                }
                currentBuffer = new ByteArrayInputStream(record.value());
                if (currentBuffer.available() > 0) {
                    return true;
                }
            }
            return false;
        }

        private String headerValue(Header header) {
            if (header == null || header.value() == null) {
                return CHUNK_TYPE_DATA;
            }
            return new String(header.value());
        }
    }

    private static final class ActiveKafkaTransfer {

        private final CompletableFuture<DataFlowResult> future;
        private final AtomicBoolean terminated = new AtomicBoolean(false);
        private volatile KafkaTopicInputStream inputStream;

        private ActiveKafkaTransfer(CompletableFuture<DataFlowResult> future) {
            this.future = future;
        }

        private void attach(KafkaTopicInputStream stream) {
            this.inputStream = stream;
            if (terminated.get()) {
                closeQuietly(stream);
            }
        }

        private void terminate() {
            terminated.set(true);
            closeQuietly(inputStream);
            complete(DataFlowResult.failure(TERMINATED_MESSAGE));
        }

        private boolean isTerminated() {
            return terminated.get();
        }

        private void complete(DataFlowResult result) {
            future.complete(result);
        }

        private void closeQuietly(KafkaTopicInputStream stream) {
            if (stream == null) {
                return;
            }
            try {
                stream.close();
            } catch (RuntimeException ignored) {
                // best-effort close during termination
            }
        }
    }
}
