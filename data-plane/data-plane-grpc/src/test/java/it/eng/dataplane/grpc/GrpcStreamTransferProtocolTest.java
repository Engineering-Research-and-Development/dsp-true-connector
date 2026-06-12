package it.eng.dataplane.grpc;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.io.SinkContext;
import it.eng.dataplane.api.io.SinkWriteResult;
import it.eng.dataplane.api.io.SinkWriter;
import it.eng.dataplane.api.io.SourceReader;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.core.registry.SinkWriterRegistry;
import it.eng.dataplane.core.registry.SourceReaderRegistry;
import it.eng.dataplane.grpc.client.GrpcChannelFactory;
import it.eng.dataplane.grpc.config.GrpcProperties;
import it.eng.dataplane.grpc.model.GrpcSessionState;
import it.eng.dataplane.grpc.model.GrpcStreamSession;
import it.eng.dataplane.grpc.proto.DataChunk;
import it.eng.dataplane.grpc.proto.DataStreamGrpc;
import it.eng.dataplane.grpc.proto.StreamRequest;
import it.eng.dataplane.grpc.registry.GrpcSessionRegistry;
import it.eng.tools.s3.util.S3Utils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GrpcStreamTransferProtocol}.
 */
@ExtendWith(MockitoExtension.class)
class GrpcStreamTransferProtocolTest {

    @Mock
    private GrpcSessionRegistry sessionRegistry;
    @Mock
    private SourceReaderRegistry sourceReaderRegistry;
    @Mock
    private SinkWriterRegistry sinkWriterRegistry;
    @Mock
    private GrpcProperties grpcProperties;
    @Mock
    private SourceReader s3SourceReader;
    @Mock
    private ControlPlaneClient controlPlaneClient;
    @Mock
    private GrpcChannelFactory channelFactory;

    private ExecutorService transferExecutor;
    private GrpcStreamTransferProtocol protocol;

    @BeforeEach
    void setUp() {
        transferExecutor = Executors.newSingleThreadExecutor();
        protocol = new GrpcStreamTransferProtocol(
                sessionRegistry,
                sourceReaderRegistry,
                grpcProperties,
                sinkWriterRegistry,
                controlPlaneClient,
                channelFactory,
                transferExecutor
        );
    }

    @AfterEach
    void tearDown() {
        transferExecutor.shutdownNow();
    }

    @Test
    @DisplayName("prepare() returns gRPC endpoint metadata for a finite session")
    void prepare_finiteSession_returnsGrpcMetadata() {
        when(grpcProperties.getHost()).thenReturn("dp-grpc");
        when(grpcProperties.getPort()).thenReturn(9094);
        when(sourceReaderRegistry.getReader("s3")).thenReturn(Optional.of(s3SourceReader));

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-finite-1")
                .datasetId("ds-1")
                .metadata(Map.of("source", Map.of("sourceType", "s3")))
                .build();

        DataFlowPrepareResponse response = protocol.prepare(message);

        assertThat(response.getProcessId()).isEqualTo("tp-finite-1");
        assertThat(response.getDataAddress()).containsEntry("endpointType", "grpc");
        assertThat(response.getDataAddress()).containsEntry("host", "dp-grpc");
        assertThat(response.getDataAddress()).containsEntry("port", "9094");
        assertThat(response.getDataAddress()).containsKey("sessionId");
        assertThat(response.getDataAddress().get("sessionId")).isNotBlank();
        assertThat(response.getDataAddress()).containsEntry("mode", "finite");
    }

    @Test
    @DisplayName("prepare() returns finite=false metadata when finite hint is 'false'")
    void prepare_nonFiniteSession_returnsNonFiniteMode() {
        when(grpcProperties.getHost()).thenReturn("dp-grpc");
        when(grpcProperties.getPort()).thenReturn(9094);
        when(sourceReaderRegistry.getReader("s3")).thenReturn(Optional.of(s3SourceReader));

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-nonfinite-1")
                .datasetId("ds-2")
                .metadata(Map.of("source", Map.of("sourceType", "s3", "finite", "false")))
                .build();

        DataFlowPrepareResponse response = protocol.prepare(message);

        assertThat(response.getDataAddress()).containsEntry("mode", "non-finite");
    }

    @Test
    @DisplayName("prepare() registers a PREPARED session in the registry")
    void prepare_registersSessionInRegistry() {
        when(grpcProperties.getHost()).thenReturn("localhost");
        when(grpcProperties.getPort()).thenReturn(9094);
        when(sourceReaderRegistry.getReader("s3")).thenReturn(Optional.of(s3SourceReader));

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-reg-1")
                .datasetId("ds-3")
                .metadata(Map.of("source", Map.of("sourceType", "s3")))
                .build();

        protocol.prepare(message);

        ArgumentCaptor<GrpcStreamSession> captor = ArgumentCaptor.forClass(GrpcStreamSession.class);
        verify(sessionRegistry).register(captor.capture());
        GrpcStreamSession registered = captor.getValue();
        assertThat(registered.getProcessId()).isEqualTo("tp-reg-1");
        assertThat(registered.getDatasetId()).isEqualTo("ds-3");
        assertThat(registered.isFinite()).isTrue();
        assertThat(registered.getState()).isEqualTo(GrpcSessionState.PREPARED);
        assertThat(registered.getSessionId()).isNotBlank();
    }

    @Test
    @DisplayName("prepare() with non-finite flag registers session with finite=false")
    void prepare_nonFiniteSession_registersWithFiniteFalse() {
        when(grpcProperties.getHost()).thenReturn("localhost");
        when(grpcProperties.getPort()).thenReturn(9094);
        when(sourceReaderRegistry.getReader("s3")).thenReturn(Optional.of(s3SourceReader));

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-nf-2")
                .datasetId("ds-4")
                .metadata(Map.of("source", Map.of("sourceType", "s3", "finite", "false")))
                .build();

        protocol.prepare(message);

        ArgumentCaptor<GrpcStreamSession> captor = ArgumentCaptor.forClass(GrpcStreamSession.class);
        verify(sessionRegistry).register(captor.capture());
        assertThat(captor.getValue().isFinite()).isFalse();
    }

    @Test
    @DisplayName("prepare() stores CP-provided source.s3 properties in the registered session")
    void prepare_storesNestedSourceS3PropertiesInRegisteredSession() {
        when(grpcProperties.getHost()).thenReturn("localhost");
        when(grpcProperties.getPort()).thenReturn(9094);
        when(sourceReaderRegistry.getReader("s3")).thenReturn(Optional.of(s3SourceReader));

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-grpc-source-s3")
                .datasetId("ds-source-s3")
                .metadata(Map.of(DataPlaneConstants.METADATA_SECTION_SOURCE, Map.of(
                        DataPlaneConstants.METADATA_FIELD_SOURCE_TYPE, "s3",
                        DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                DataPlaneConstants.METADATA_S3_BUCKET_NAME, "cp-source-bucket",
                                DataPlaneConstants.METADATA_S3_OBJECT_KEY, "cp-source-object",
                                DataPlaneConstants.METADATA_S3_REGION, "eu-west-1",
                                DataPlaneConstants.METADATA_S3_ACCESS_KEY, "cp-access-key",
                                DataPlaneConstants.METADATA_S3_SECRET_KEY, "cp-secret-key",
                                DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, "http://minio:9000"))))
                .build();

        protocol.prepare(message);

        ArgumentCaptor<GrpcStreamSession> captor = ArgumentCaptor.forClass(GrpcStreamSession.class);
        verify(sessionRegistry).register(captor.capture());
        assertThat(captor.getValue().getSourceProperties())
                .containsEntry(S3Utils.BUCKET_NAME, "cp-source-bucket")
                .containsEntry(S3Utils.OBJECT_KEY, "cp-source-object")
                .containsEntry(S3Utils.REGION, "eu-west-1")
                .containsEntry(S3Utils.ACCESS_KEY, "cp-access-key")
                .containsEntry(S3Utils.SECRET_KEY, "cp-secret-key")
                .containsEntry(S3Utils.ENDPOINT_OVERRIDE, "http://minio:9000");
    }

    @Test
    @DisplayName("prepare() with unknown sourceType throws IllegalArgumentException")
    void prepare_unknownSourceType_throwsIllegalArgument() {
        when(sourceReaderRegistry.getReader("unknown")).thenReturn(Optional.empty());

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-bad-1")
                .datasetId("ds-5")
                .metadata(Map.of("source", Map.of("sourceType", "unknown")))
                .build();

        assertThatThrownBy(() -> protocol.prepare(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No SourceReader available for sourceType: unknown");
    }

    @Test
    @DisplayName("prepare() defaults sourceType to 's3' when dataAddress is absent")
    void prepare_nullDataAddress_defaultsToS3SourceType() {
        when(grpcProperties.getHost()).thenReturn("localhost");
        when(grpcProperties.getPort()).thenReturn(9094);
        when(sourceReaderRegistry.getReader("s3")).thenReturn(Optional.of(s3SourceReader));

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-default-1")
                .datasetId("ds-6")
                .build();

        DataFlowPrepareResponse response = protocol.prepare(message);

        assertThat(response.getDataAddress()).containsEntry("mode", "finite");
    }

    @Test
    @DisplayName("getProtocolId() returns 'stream:grpc'")
    void getProtocolId_returnsStreamGrpc() {
        assertThat(protocol.getProtocolId()).isEqualTo("stream:grpc");
    }

    @Test
    @DisplayName("terminateTransfer() removes session from registry and returns success")
    void terminateTransfer_removesSessionAndReturnsSuccess() {
        CompletableFuture<DataFlowResult> future = protocol.terminateTransfer("tp-term-1");
        DataFlowResult result = future.join();

        assertThat(result.isSuccess()).isTrue();
        verify(sessionRegistry).removeByProcessId("tp-term-1");
    }

    @Test
    @DisplayName("initiateTransfer() writes finite stream to sink and sends completed callback")
    void initiateTransfer_finiteStream_writesToSinkAndCallsSendCompleted() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .addService(new FiniteChunkService(List.of("hello ".getBytes(StandardCharsets.UTF_8),
                        "world".getBytes(StandardCharsets.UTF_8))))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).build();
        TestSinkWriter sinkWriter = new TestSinkWriter();

        when(channelFactory.create("grpc-host", 9094)).thenReturn(channel);
        when(sinkWriterRegistry.getWriter("s3")).thenReturn(Optional.of(sinkWriter));

        Map<String, String> dataAddress = new java.util.HashMap<>(Map.of(
                "host", "grpc-host",
                "port", "9094",
                "sessionId", "sess-1",
                "mode", "finite"
        ));
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-finite-1")
                .processId("tp-1")
                .datasetId("ds-1")
                .transferType("stream:grpc")
                .callbackAddress("http://cp:8080")
                .dataAddress(dataAddress)
                .metadata(java.util.Map.of(
                        DataPlaneConstants.METADATA_SECTION_SINK, java.util.Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3, java.util.Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "bucket-a",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "tp-1"))))
                .build();

        try {
            DataFlowResult result = protocol.initiateTransfer(dataFlow).get(5, TimeUnit.SECONDS);

            assertThat(result.isSuccess()).isTrue();
            assertThat(sinkWriter.getReceivedText()).isEqualTo("hello world");
            assertThat(sinkWriter.getLastContext().getProperties()).containsEntry(S3Utils.BUCKET_NAME, "bucket-a");
            assertThat(sinkWriter.getLastContext().getProperties()).containsEntry(S3Utils.OBJECT_KEY, "tp-1");
            verify(controlPlaneClient).sendStarted("http://cp:8080", "tp-1", dataAddress);
            verify(controlPlaneClient).sendCompleted("http://cp:8080", "tp-1", dataAddress);
            verify(controlPlaneClient, never()).sendErrored("http://cp:8080", "tp-1", "transfer terminated");
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("initiateTransfer() keeps non-finite stream open until terminateTransfer is called")
    void initiateTransfer_nonFiniteStream_doesNotCompleteUntilTerminated() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        BlockingChunkService service = new BlockingChunkService("event-1".getBytes(StandardCharsets.UTF_8));
        Server server = InProcessServerBuilder.forName(serverName)
                .addService(service)
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).build();
        TestSinkWriter sinkWriter = new TestSinkWriter();

        when(channelFactory.create("grpc-host", 9094)).thenReturn(channel);
        when(sinkWriterRegistry.getWriter("s3")).thenReturn(Optional.of(sinkWriter));

        Map<String, String> dataAddress = Map.of(
                "host", "grpc-host",
                "port", "9094",
                "sessionId", "sess-nonfinite",
                "mode", "non-finite"
        );
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-nonfinite-1")
                .processId("tp-2")
                .datasetId("ds-2")
                .transferType("stream:grpc")
                .callbackAddress("http://cp:8080")
                .dataAddress(dataAddress)
                .build();

        try {
            CompletableFuture<DataFlowResult> future = protocol.initiateTransfer(dataFlow);
            assertThat(sinkWriter.awaitFirstRead()).isTrue();
            assertThat(future).isNotDone();

            DataFlowResult terminateResult = protocol.terminateTransfer("df-nonfinite-1").get(5, TimeUnit.SECONDS);
            assertThat(terminateResult.isSuccess()).isTrue();

            DataFlowResult result = future.get(5, TimeUnit.SECONDS);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("transfer terminated");
            assertThat(channel.isShutdown()).isTrue();
            verify(controlPlaneClient).sendStarted("http://cp:8080", "tp-2", dataAddress);
            verify(controlPlaneClient, never()).sendCompleted("http://cp:8080", "tp-2", dataAddress);
            verify(controlPlaneClient, never()).sendErrored("http://cp:8080", "tp-2", "transfer terminated");
        } finally {
            service.release();
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("initiateTransfer() fails immediately when data address is missing")
    void initiateTransfer_missingDataAddress_failsImmediately() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-missing-1")
                .processId("tp-3")
                .transferType("stream:grpc")
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).join();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("dataAddress is required for stream:grpc");
    }

    @Test
    @DisplayName("initiateTransfer() fails immediately when required transport metadata is missing")
    void initiateTransfer_missingTransportMetadata_failsImmediately() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-missing-2")
                .processId("tp-4")
                .transferType("stream:grpc")
                .dataAddress(Map.of("port", "9094", "sessionId", "sess-1"))
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).join();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Missing transport metadata");
    }

    @Test
    @DisplayName("initiateTransfer() fails immediately when port is invalid")
    void initiateTransfer_invalidPort_failsImmediately() {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-invalid-port")
                .processId("tp-5")
                .transferType("stream:grpc")
                .dataAddress(Map.of("host", "grpc-host", "port", "invalid", "sessionId", "sess-1"))
                .build();

        DataFlowResult result = protocol.initiateTransfer(dataFlow).join();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Invalid port value: invalid");
    }

    @Test
    @DisplayName("initiateTransfer() returns failure and sends errored callback when provider rejects the session")
    void initiateTransfer_unknownProviderSession_returnsFailure() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .addService(new MissingSessionService())
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).build();
        TestSinkWriter sinkWriter = new TestSinkWriter();

        when(channelFactory.create("grpc-host", 9094)).thenReturn(channel);
        when(sinkWriterRegistry.getWriter("s3")).thenReturn(Optional.of(sinkWriter));

        Map<String, String> dataAddress = Map.of(
                "host", "grpc-host",
                "port", "9094",
                "sessionId", "missing-session",
                "mode", "finite"
        );
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-missing-session")
                .processId("tp-6")
                .transferType("stream:grpc")
                .callbackAddress("http://cp:8080")
                .dataAddress(dataAddress)
                .build();

        try {
            DataFlowResult result = protocol.initiateTransfer(dataFlow).get(5, TimeUnit.SECONDS);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("NOT_FOUND");
            verify(controlPlaneClient).sendErrored("http://cp:8080", "tp-6", result.getErrorMessage());
            verify(controlPlaneClient, never()).sendCompleted("http://cp:8080", "tp-6", dataAddress);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("prepare() persists CP-provided source S3 properties into the registered session")
    void prepare_persistsCpProvidedSourceS3PropertiesInSession() {
        when(grpcProperties.getHost()).thenReturn("localhost");
        when(grpcProperties.getPort()).thenReturn(9094);
        when(sourceReaderRegistry.getReader("s3")).thenReturn(Optional.of(s3SourceReader));

        DataFlowPrepareMessage message = DataFlowPrepareMessage.Builder.newInstance()
                .processId("tp-source-s3-1")
                .datasetId("ds-src-1")
                .metadata(Map.of(DataPlaneConstants.METADATA_SECTION_SOURCE, Map.of(
                        DataPlaneConstants.METADATA_FIELD_SOURCE_TYPE, "s3",
                        DataPlaneConstants.METADATA_SECTION_S3, Map.of(
                                DataPlaneConstants.METADATA_S3_BUCKET_NAME, "cp-source-bucket",
                                DataPlaneConstants.METADATA_S3_REGION, "eu-west-1",
                                DataPlaneConstants.METADATA_S3_ACCESS_KEY, "cp-access",
                                DataPlaneConstants.METADATA_S3_SECRET_KEY, "cp-secret",
                                DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, "http://cp-minio:9000")
                )))
                .build();

        protocol.prepare(message);

        ArgumentCaptor<GrpcStreamSession> captor = ArgumentCaptor.forClass(GrpcStreamSession.class);
        verify(sessionRegistry).register(captor.capture());
        Map<String, String> sourceProps = captor.getValue().getSourceProperties();
        assertThat(sourceProps).containsEntry(S3Utils.BUCKET_NAME, "cp-source-bucket");
        assertThat(sourceProps).containsEntry(S3Utils.OBJECT_KEY, "ds-src-1");
        assertThat(sourceProps).containsEntry(S3Utils.REGION, "eu-west-1");
        assertThat(sourceProps).containsEntry(S3Utils.ACCESS_KEY, "cp-access");
        assertThat(sourceProps).containsEntry(S3Utils.SECRET_KEY, "cp-secret");
        assertThat(sourceProps).containsEntry(S3Utils.ENDPOINT_OVERRIDE, "http://cp-minio:9000");
    }

    @Test
    @DisplayName("initiateTransfer() builds sink context from CP-provided metadata.sink.s3 properties")
    void initiateTransfer_usesCpProvidedSinkPropertiesFromDataAddress() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .addService(new FiniteChunkService(List.of("data".getBytes(StandardCharsets.UTF_8))))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).build();
        TestSinkWriter sinkWriter = new TestSinkWriter();

        when(channelFactory.create("grpc-host", 9094)).thenReturn(channel);
        when(sinkWriterRegistry.getWriter("s3")).thenReturn(Optional.of(sinkWriter));

        Map<String, String> dataAddress = new java.util.HashMap<>();
        dataAddress.put("host", "grpc-host");
        dataAddress.put("port", "9094");
        dataAddress.put("sessionId", "sess-sink-cp");
        dataAddress.put("mode", "finite");
        // S3 sink credentials come from metadata.sink.s3, not flat dataAddress keys

        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-sink-cp-1")
                .processId("tp-sink-cp")
                .datasetId("ds-sink-cp")
                .transferType("stream:grpc")
                .callbackAddress("http://cp:8080")
                .dataAddress(dataAddress)
                .metadata(java.util.Map.of(
                        DataPlaneConstants.METADATA_SECTION_SINK, java.util.Map.of(
                                DataPlaneConstants.METADATA_SECTION_S3, java.util.Map.of(
                                        DataPlaneConstants.METADATA_S3_BUCKET_NAME, "cp-sink-bucket",
                                        DataPlaneConstants.METADATA_S3_OBJECT_KEY, "cp-sink-key",
                                        DataPlaneConstants.METADATA_S3_REGION, "us-west-2",
                                        DataPlaneConstants.METADATA_S3_ACCESS_KEY, "cp-sink-access",
                                        DataPlaneConstants.METADATA_S3_SECRET_KEY, "cp-sink-secret",
                                        DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, "http://cp-minio:9000"))))
                .build();

        try {
            DataFlowResult result = protocol.initiateTransfer(dataFlow).get(5, TimeUnit.SECONDS);
            assertThat(result.isSuccess()).isTrue();
            Map<String, String> sinkProps = sinkWriter.getLastContext().getProperties();
            assertThat(sinkProps).containsEntry(S3Utils.BUCKET_NAME, "cp-sink-bucket");
            assertThat(sinkProps).containsEntry(S3Utils.OBJECT_KEY, "cp-sink-key");
            assertThat(sinkProps).containsEntry(S3Utils.REGION, "us-west-2");
            assertThat(sinkProps).containsEntry(S3Utils.ACCESS_KEY, "cp-sink-access");
            assertThat(sinkProps).containsEntry(S3Utils.SECRET_KEY, "cp-sink-secret");
            assertThat(sinkProps).containsEntry(S3Utils.ENDPOINT_OVERRIDE, "http://cp-minio:9000");
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    /**
     * Test sink writer that captures streamed bytes.
     */
    private static final class TestSinkWriter implements SinkWriter {

        private final java.util.concurrent.CountDownLatch firstReadLatch = new java.util.concurrent.CountDownLatch(1);
        private byte[] received = new byte[0];
        private SinkContext lastContext;

        @Override
        public String getSinkType() {
            return "s3";
        }

        @Override
        public SinkWriteResult write(InputStream data, SinkContext context) {
            this.lastContext = context;
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4];
                int bytesRead;
                while ((bytesRead = data.read(buffer)) != -1) {
                    firstReadLatch.countDown();
                    outputStream.write(buffer, 0, bytesRead);
                }
                received = outputStream.toByteArray();
                return SinkWriteResult.success("etag-123");
            } catch (IOException exception) {
                return SinkWriteResult.failure(exception.getMessage());
            }
        }

        private boolean awaitFirstRead() throws InterruptedException {
            return firstReadLatch.await(5, TimeUnit.SECONDS);
        }

        private String getReceivedText() {
            return new String(received, StandardCharsets.UTF_8);
        }

        private SinkContext getLastContext() {
            return lastContext;
        }
    }

    @Test
    @DisplayName("initiateTransfer() treats server-initiated EOF on a non-finite stream as an error")
    void initiateTransfer_nonFiniteStream_serverEof_treatsAsError() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .addService(new EofChunkService(List.of("chunk-1".getBytes(StandardCharsets.UTF_8))))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).build();
        TestSinkWriter sinkWriter = new TestSinkWriter();

        when(channelFactory.create("grpc-host", 9094)).thenReturn(channel);
        when(sinkWriterRegistry.getWriter("s3")).thenReturn(Optional.of(sinkWriter));

        Map<String, String> dataAddress = Map.of(
                "host", "grpc-host",
                "port", "9094",
                "sessionId", "sess-nonfinite-eof",
                "mode", "non-finite"
        );
        DataFlow dataFlow = DataFlow.Builder.newInstance()
                .dataFlowId("df-nonfinite-eof-1")
                .processId("tp-nonfinite-eof")
                .datasetId("ds-nonfinite-eof")
                .transferType("stream:grpc")
                .callbackAddress("http://cp:8080")
                .dataAddress(dataAddress)
                .build();

        try {
            DataFlowResult result = protocol.initiateTransfer(dataFlow).get(5, TimeUnit.SECONDS);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("non-finite");
            verify(controlPlaneClient).sendStarted("http://cp:8080", "tp-nonfinite-eof", dataAddress);
            verify(controlPlaneClient).sendErrored("http://cp:8080", "tp-nonfinite-eof", result.getErrorMessage());
            verify(controlPlaneClient, never()).sendCompleted("http://cp:8080", "tp-nonfinite-eof", dataAddress);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    /**
     * Finite in-process gRPC service used by initiateTransfer tests.
     */
    private static final class FiniteChunkService extends DataStreamGrpc.DataStreamImplBase {

        private final List<byte[]> chunks;

        private FiniteChunkService(List<byte[]> chunks) {
            this.chunks = chunks;
        }

        @Override
        public void stream(StreamRequest request, StreamObserver<DataChunk> responseObserver) {
            for (byte[] chunk : chunks) {
                responseObserver.onNext(DataChunk.newBuilder()
                        .setPayload(ByteString.copyFrom(chunk))
                        .build());
            }
            responseObserver.onCompleted();
        }
    }

    /**
     * Non-finite in-process gRPC service used by terminateTransfer tests.
     */
    private static final class BlockingChunkService extends DataStreamGrpc.DataStreamImplBase {

        private final byte[] firstChunk;
        private final java.util.concurrent.CountDownLatch releaseLatch = new java.util.concurrent.CountDownLatch(1);

        private BlockingChunkService(byte[] firstChunk) {
            this.firstChunk = firstChunk;
        }

        @Override
        public void stream(StreamRequest request, StreamObserver<DataChunk> responseObserver) {
            responseObserver.onNext(DataChunk.newBuilder()
                    .setPayload(ByteString.copyFrom(firstChunk))
                    .build());
            try {
                releaseLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        private void release() {
            releaseLatch.countDown();
        }
    }

    /**
     * In-process gRPC service that always returns NOT_FOUND.
     */
    private static final class MissingSessionService extends DataStreamGrpc.DataStreamImplBase {

        @Override
        public void stream(StreamRequest request, StreamObserver<DataChunk> responseObserver) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("No session found for sessionId: " + request.getSessionId())
                    .asRuntimeException());
        }
    }

    /**
     * In-process gRPC service that sends chunks then calls {@code onCompleted()},
     * simulating a server-initiated EOF on a stream declared as non-finite by the consumer.
     */
    private static final class EofChunkService extends DataStreamGrpc.DataStreamImplBase {

        private final List<byte[]> chunks;

        private EofChunkService(List<byte[]> chunks) {
            this.chunks = chunks;
        }

        @Override
        public void stream(StreamRequest request, StreamObserver<DataChunk> responseObserver) {
            for (byte[] chunk : chunks) {
                responseObserver.onNext(DataChunk.newBuilder()
                        .setPayload(ByteString.copyFrom(chunk))
                        .build());
            }
            responseObserver.onCompleted();
        }
    }
}
