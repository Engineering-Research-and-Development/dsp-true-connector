package it.eng.dataplane.grpc.server;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import it.eng.dataplane.api.io.SourceContext;
import it.eng.dataplane.api.io.SourceOpenResult;
import it.eng.dataplane.api.io.SourceReader;
import it.eng.dataplane.core.registry.SourceReaderRegistry;
import it.eng.dataplane.grpc.model.GrpcStreamSession;
import it.eng.dataplane.grpc.proto.DataChunk;
import it.eng.dataplane.grpc.proto.DataStreamGrpc;
import it.eng.dataplane.grpc.proto.StreamRequest;
import it.eng.dataplane.grpc.registry.GrpcSessionRegistry;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.util.S3Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Provider-side gRPC service that streams artifact bytes to a consumer.
 */
@Slf4j
@Component
public class DataStreamService extends DataStreamGrpc.DataStreamImplBase {

    static final int CHUNK_SIZE = 64 * 1024;
    private static final String SOURCE_TYPE_S3 = "s3";

    private final GrpcSessionRegistry sessionRegistry;
    private final SourceReaderRegistry sourceReaderRegistry;
    private final S3Properties s3Properties;

    /**
     * Creates the data-stream service.
     *
     * @param sessionRegistry session registry
     * @param sourceReaderRegistry source reader registry
     * @param s3Properties S3 configuration
     */
    public DataStreamService(GrpcSessionRegistry sessionRegistry,
                             SourceReaderRegistry sourceReaderRegistry,
                             S3Properties s3Properties) {
        this.sessionRegistry = sessionRegistry;
        this.sourceReaderRegistry = sourceReaderRegistry;
        this.s3Properties = s3Properties;
    }

    /**
     * Streams data chunks for the requested session.
     *
     * @param request stream request carrying the session identifier
     * @param responseObserver response observer for outgoing chunks
     */
    @Override
    public void stream(StreamRequest request, StreamObserver<DataChunk> responseObserver) {
        String sessionId = request.getSessionId();
        log.info("Received gRPC stream request for sessionId={}", sessionId);

        Optional<GrpcStreamSession> sessionOptional = sessionRegistry.findBySessionId(sessionId);
        if (sessionOptional.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("No session found for sessionId: " + sessionId)
                    .asRuntimeException());
            return;
        }

        Optional<SourceReader> readerOptional = sourceReaderRegistry.getReader(SOURCE_TYPE_S3);
        if (readerOptional.isEmpty()) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("No SourceReader for type: " + SOURCE_TYPE_S3)
                    .asRuntimeException());
            return;
        }

        GrpcStreamSession session = sessionOptional.get();
        SourceContext sourceContext = SourceContext.Builder.newInstance()
                .properties(buildSourceProperties(session))
                .build();

        try (SourceOpenResult openResult = readerOptional.get().open(sourceContext)) {
            if (!openResult.isSuccess()) {
                responseObserver.onError(Status.INTERNAL
                        .withDescription(openResult.getErrorMessage())
                        .asRuntimeException());
                return;
            }

            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = openResult.getStream().read(buffer)) != -1) {
                if (sessionRegistry.findBySessionId(sessionId).isEmpty()) {
                    log.info("Stopping gRPC stream because sessionId={} was removed", sessionId);
                    responseObserver.onCompleted();
                    return;
                }
                responseObserver.onNext(DataChunk.newBuilder()
                        .setPayload(ByteString.copyFrom(buffer, 0, bytesRead))
                        .build());
            }
            responseObserver.onCompleted();
        } catch (IOException | RuntimeException exception) {
            log.error("Failed to stream sessionId={}: {}", sessionId, exception.getMessage(), exception);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(exception.getMessage())
                    .asRuntimeException());
        }
    }

    private Map<String, String> buildSourceProperties(GrpcStreamSession session) {
        Map<String, String> properties = new HashMap<>();
        properties.put(S3Utils.BUCKET_NAME, s3Properties.getBucketName());
        properties.put(S3Utils.OBJECT_KEY, session.getDatasetId());
        return properties;
    }
}
