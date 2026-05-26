package it.eng.dataplane.grpc;

import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.registry.SourceReaderRegistry;
import it.eng.dataplane.grpc.config.GrpcProperties;
import it.eng.dataplane.grpc.model.GrpcSessionState;
import it.eng.dataplane.grpc.model.GrpcStreamSession;
import it.eng.dataplane.grpc.registry.GrpcSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * gRPC-streaming transfer protocol implementation.
 *
 * <p>Handles the DPS {@code prepare} phase by allocating a {@link GrpcStreamSession} and
 * returning transport coordinates (host, port, sessionId, mode) in the response
 * {@code dataAddress}. The consumer uses those coordinates to establish a gRPC connection
 * when the transfer is started.</p>
 *
 * <p>Provider-side streaming ({@code initiateTransfer}) will be implemented in a later task.
 * This class focuses on session allocation and prepare metadata generation.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcStreamTransferProtocol implements DataTransferProtocol {

    /** Protocol identifier used for routing and registration. */
    public static final String PROTOCOL_ID = "stream:grpc";

    static final String ENDPOINT_TYPE_KEY = "endpointType";
    static final String HOST_KEY = "host";
    static final String PORT_KEY = "port";
    static final String SESSION_ID_KEY = "sessionId";
    static final String MODE_KEY = "mode";
    static final String SOURCE_TYPE_KEY = "sourceType";
    static final String FINITE_KEY = "finite";
    static final String MODE_FINITE = "finite";
    static final String MODE_NON_FINITE = "non-finite";
    static final String DEFAULT_SOURCE_TYPE = "s3";

    private final GrpcSessionRegistry sessionRegistry;
    private final SourceReaderRegistry sourceReaderRegistry;
    private final GrpcProperties grpcProperties;

    /**
     * Returns the unique protocol identifier for this transport.
     *
     * @return {@value PROTOCOL_ID}
     */
    @Override
    public String getProtocolId() {
        return PROTOCOL_ID;
    }

    /**
     * Allocates a gRPC stream session and returns transport metadata.
     *
     * <p>Steps performed:
     * <ol>
     *   <li>Resolves the {@code sourceType} from the incoming {@code dataAddress} (defaults to
     *       {@code s3}).</li>
     *   <li>Validates that a {@link it.eng.dataplane.api.io.SourceReader} is registered for
     *       that source type.</li>
     *   <li>Determines the session mode from the {@code finite} hint ({@code true} if absent).</li>
     *   <li>Allocates a {@link GrpcStreamSession} with a fresh UUID and stores it in the
     *       {@link GrpcSessionRegistry}.</li>
     *   <li>Returns a {@link DataFlowPrepareResponse} whose {@code dataAddress} carries:
     *       {@code endpointType}, {@code host}, {@code port}, {@code sessionId}, and
     *       {@code mode}.</li>
     * </ol>
     * </p>
     *
     * @param message the prepare message from the Control Plane
     * @return response containing gRPC transport coordinates
     * @throws IllegalArgumentException if no SourceReader is registered for the requested source type
     */
    @Override
    public DataFlowPrepareResponse prepare(DataFlowPrepareMessage message) {
        Map<String, String> dataAddress = message.getDataAddress() != null
                ? message.getDataAddress()
                : Map.of();

        String sourceType = dataAddress.getOrDefault(SOURCE_TYPE_KEY, DEFAULT_SOURCE_TYPE);
        sourceReaderRegistry.getReader(sourceType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No SourceReader available for sourceType: " + sourceType));

        boolean finite = !"false".equalsIgnoreCase(dataAddress.get(FINITE_KEY));
        String mode = finite ? MODE_FINITE : MODE_NON_FINITE;

        String sessionId = UUID.randomUUID().toString();
        GrpcStreamSession session = GrpcStreamSession.Builder.newInstance()
                .sessionId(sessionId)
                .processId(message.getProcessId())
                .datasetId(message.getDatasetId())
                .finite(finite)
                .tenantId(message.getParticipantId())
                .state(GrpcSessionState.PREPARED)
                .build();
        sessionRegistry.register(session);

        log.info("Prepared gRPC session sessionId={} processId={} mode={}",
                sessionId, message.getProcessId(), mode);

        Map<String, String> responseDataAddress = new LinkedHashMap<>();
        responseDataAddress.put(ENDPOINT_TYPE_KEY, "grpc");
        responseDataAddress.put(HOST_KEY, grpcProperties.getHost());
        responseDataAddress.put(PORT_KEY, String.valueOf(grpcProperties.getPort()));
        responseDataAddress.put(SESSION_ID_KEY, sessionId);
        responseDataAddress.put(MODE_KEY, mode);

        return DataFlowPrepareResponse.Builder.newInstance()
                .processId(message.getProcessId())
                .dataAddress(responseDataAddress)
                .build();
    }

    /**
     * Initiates a gRPC streaming transfer.
     *
     * <p>Provider-side streaming is not yet implemented; returns a failure result.
     * This will be implemented in a later task.</p>
     *
     * @param dataFlow the data flow to initiate
     * @return future with a failure result
     */
    @Override
    public CompletableFuture<DataFlowResult> initiateTransfer(DataFlow dataFlow) {
        log.warn("gRPC stream transfer initiation not yet implemented for processId={}", dataFlow.getProcessId());
        return CompletableFuture.completedFuture(
                DataFlowResult.failure("gRPC stream transfer initiation not yet implemented"));
    }

    /**
     * Suspends a gRPC transfer.
     *
     * <p>Suspend is not supported for the gRPC streaming transport.</p>
     *
     * @param dataFlowId the data flow ID
     * @return future with a failure result
     */
    @Override
    public CompletableFuture<DataFlowResult> suspendTransfer(String dataFlowId) {
        log.warn("Suspend is not supported for stream:grpc dataFlowId={}", dataFlowId);
        return CompletableFuture.completedFuture(
                DataFlowResult.failure("suspend not supported for stream:grpc"));
    }

    /**
     * Resumes a suspended gRPC transfer.
     *
     * <p>Resume is not supported for the gRPC streaming transport.</p>
     *
     * @param dataFlowId the data flow ID
     * @return future with a failure result
     */
    @Override
    public CompletableFuture<DataFlowResult> resumeTransfer(String dataFlowId) {
        log.warn("Resume is not supported for stream:grpc dataFlowId={}", dataFlowId);
        return CompletableFuture.completedFuture(
                DataFlowResult.failure("resume not supported for stream:grpc"));
    }

    /**
     * Terminates a gRPC transfer and releases the associated session.
     *
     * @param dataFlowId the transfer process ID whose session should be released
     * @return future with a success result
     */
    @Override
    public CompletableFuture<DataFlowResult> terminateTransfer(String dataFlowId) {
        log.info("Terminating stream:grpc transfer processId={}", dataFlowId);
        sessionRegistry.removeByProcessId(dataFlowId);
        return CompletableFuture.completedFuture(DataFlowResult.success());
    }
}
