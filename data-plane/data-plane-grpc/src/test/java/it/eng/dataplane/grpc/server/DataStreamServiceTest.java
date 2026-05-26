package it.eng.dataplane.grpc.server;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import it.eng.dataplane.api.io.SourceContext;
import it.eng.dataplane.api.io.SourceOpenResult;
import it.eng.dataplane.api.io.SourceReader;
import it.eng.dataplane.core.registry.SourceReaderRegistry;
import it.eng.dataplane.grpc.model.GrpcSessionState;
import it.eng.dataplane.grpc.model.GrpcStreamSession;
import it.eng.dataplane.grpc.proto.DataChunk;
import it.eng.dataplane.grpc.proto.DataStreamGrpc;
import it.eng.dataplane.grpc.proto.StreamRequest;
import it.eng.dataplane.grpc.registry.GrpcSessionRegistry;
import it.eng.tools.s3.properties.S3Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataStreamService}.
 */
class DataStreamServiceTest {

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("stream returns S3-backed bytes for a known session")
    void stream_knownSession_streamsSourceData() throws Exception {
        GrpcSessionRegistry sessionRegistry = new GrpcSessionRegistry();
        sessionRegistry.register(GrpcStreamSession.Builder.newInstance()
                .sessionId("sess-1")
                .processId("tp-1")
                .datasetId("dataset-1")
                .tenantId("tenant-1")
                .finite(true)
                .state(GrpcSessionState.PREPARED)
                .build());
        RecordingSourceReader sourceReader = new RecordingSourceReader("hello world");
        DataStreamService service = new DataStreamService(
                sessionRegistry,
                new SourceReaderRegistry(List.of(sourceReader)),
                s3Properties("bucket-a")
        );

        DataStreamGrpc.DataStreamBlockingStub stub = startStub(service);
        Iterator<DataChunk> iterator = stub.stream(StreamRequest.newBuilder().setSessionId("sess-1").build());

        byte[] bytes = readPayload(iterator);

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("hello world");
        assertThat(sourceReader.getLastContext().getProperties()).containsEntry("bucketName", "bucket-a");
        assertThat(sourceReader.getLastContext().getProperties()).containsEntry("objectKey", "dataset-1");
    }

    @Test
    @DisplayName("stream returns NOT_FOUND when the session does not exist")
    void stream_missingSession_returnsNotFound() throws Exception {
        DataStreamService service = new DataStreamService(
                new GrpcSessionRegistry(),
                new SourceReaderRegistry(List.of(new RecordingSourceReader("unused"))),
                s3Properties("bucket-a")
        );

        DataStreamGrpc.DataStreamBlockingStub stub = startStub(service);
        Iterator<DataChunk> iterator = stub.stream(StreamRequest.newBuilder().setSessionId("missing").build());

        assertThatThrownBy(iterator::hasNext)
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(exception -> assertThat(((StatusRuntimeException) exception).getStatus().getCode())
                        .isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    @DisplayName("stream returns INTERNAL when the source cannot be opened")
    void stream_sourceOpenFails_returnsInternalError() throws Exception {
        GrpcSessionRegistry sessionRegistry = new GrpcSessionRegistry();
        sessionRegistry.register(GrpcStreamSession.Builder.newInstance()
                .sessionId("sess-2")
                .processId("tp-2")
                .datasetId("dataset-2")
                .tenantId("tenant-2")
                .finite(true)
                .state(GrpcSessionState.PREPARED)
                .build());
        DataStreamService service = new DataStreamService(
                sessionRegistry,
                new SourceReaderRegistry(List.of(new FailingSourceReader("cannot open source"))),
                s3Properties("bucket-a")
        );

        DataStreamGrpc.DataStreamBlockingStub stub = startStub(service);
        Iterator<DataChunk> iterator = stub.stream(StreamRequest.newBuilder().setSessionId("sess-2").build());

        assertThatThrownBy(iterator::hasNext)
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(exception -> {
                    StatusRuntimeException statusException = (StatusRuntimeException) exception;
                    assertThat(statusException.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(statusException.getStatus().getDescription()).contains("cannot open source");
                });
    }

    private DataStreamGrpc.DataStreamBlockingStub startStub(DataStreamService service) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).build();
        return DataStreamGrpc.newBlockingStub(channel);
    }

    private byte[] readPayload(Iterator<DataChunk> iterator) {
        List<byte[]> chunks = new ArrayList<>();
        int totalLength = 0;
        while (iterator.hasNext()) {
            byte[] bytes = iterator.next().getPayload().toByteArray();
            chunks.add(bytes);
            totalLength += bytes.length;
        }
        byte[] payload = new byte[totalLength];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, payload, offset, chunk.length);
            offset += chunk.length;
        }
        return payload;
    }

    private S3Properties s3Properties(String bucketName) {
        S3Properties properties = new S3Properties();
        properties.setBucketName(bucketName);
        return properties;
    }

    /**
     * Source reader that returns a fixed payload and records the source context.
     */
    private static final class RecordingSourceReader implements SourceReader {

        private final byte[] payload;
        private SourceContext lastContext;

        private RecordingSourceReader(String payload) {
            this.payload = payload.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getSourceType() {
            return "s3";
        }

        @Override
        public SourceOpenResult open(SourceContext context) {
            this.lastContext = context;
            return SourceOpenResult.success(
                    new ByteArrayInputStream(payload),
                    "application/octet-stream",
                    (long) payload.length,
                    true
            );
        }

        private SourceContext getLastContext() {
            return lastContext;
        }
    }

    /**
     * Source reader that always fails.
     */
    private static final class FailingSourceReader implements SourceReader {

        private final String errorMessage;

        private FailingSourceReader(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        public String getSourceType() {
            return "s3";
        }

        @Override
        public SourceOpenResult open(SourceContext context) {
            return SourceOpenResult.failure(errorMessage);
        }
    }
}
