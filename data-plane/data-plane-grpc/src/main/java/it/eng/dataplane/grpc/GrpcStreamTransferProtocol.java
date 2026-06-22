package it.eng.dataplane.grpc;

import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareMetadata;
import it.eng.dataplane.api.message.DataFlowPrepareMetadataSection;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.io.SinkContext;
import it.eng.dataplane.api.io.SinkWriteResult;
import it.eng.dataplane.api.io.SinkWriter;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.spi.DataTransferProtocol;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.core.registry.SinkWriterRegistry;
import it.eng.dataplane.core.registry.SourceReaderRegistry;
import it.eng.dataplane.grpc.client.GrpcChannelFactory;
import it.eng.dataplane.grpc.config.GrpcProperties;
import it.eng.dataplane.grpc.io.GrpcChunkInputStream;
import it.eng.dataplane.grpc.model.GrpcSessionState;
import it.eng.dataplane.grpc.model.GrpcStreamSession;
import it.eng.dataplane.grpc.proto.DataChunk;
import it.eng.dataplane.grpc.proto.DataStreamGrpc;
import it.eng.dataplane.grpc.proto.StreamRequest;
import it.eng.dataplane.grpc.registry.GrpcSessionRegistry;
import it.eng.dataplane.s3.service.FiniteArtifactViewPrepareService;
import it.eng.tools.s3.util.S3Utils;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

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
public class GrpcStreamTransferProtocol implements DataTransferProtocol {

    /** Protocol identifier used for routing and registration. */
    public static final String PROTOCOL_ID = "stream:grpc";

    static final String ENDPOINT_TYPE_KEY = "endpointType";
    static final String HOST_KEY = "host";
    static final String PORT_KEY = "port";
    static final String SESSION_ID_KEY = "sessionId";
    static final String MODE_KEY = "mode";
    static final String MODE_FINITE = "finite";
    static final String MODE_NON_FINITE = "non-finite";
    static final String DEFAULT_SOURCE_TYPE = "s3";

    private final GrpcSessionRegistry sessionRegistry;
    private final SourceReaderRegistry sourceReaderRegistry;
    private final GrpcProperties grpcProperties;
    private final SinkWriterRegistry sinkWriterRegistry;
    private final ControlPlaneClient controlPlaneClient;
    private final GrpcChannelFactory channelFactory;
    private final Executor transferExecutor;
    private final FiniteArtifactViewPrepareService finiteArtifactViewPrepareService;

    private final ConcurrentHashMap<String, ManagedChannel> activeChannels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<DataFlowResult>> activeTransfers = new ConcurrentHashMap<>();

    /**
     * Creates the gRPC transfer protocol.
     *
     * @param sessionRegistry session registry
     * @param sourceReaderRegistry source reader registry
     * @param grpcProperties gRPC server properties
     * @param sinkWriterRegistry sink writer registry
     * @param controlPlaneClient control-plane callback client
     * @param channelFactory managed-channel factory
     * @param transferExecutor async executor
     * @param finiteArtifactViewPrepareService helper for finite VIEW prepare responses
     */
    public GrpcStreamTransferProtocol(GrpcSessionRegistry sessionRegistry,
                                      SourceReaderRegistry sourceReaderRegistry,
                                      GrpcProperties grpcProperties,
                                      SinkWriterRegistry sinkWriterRegistry,
                                      ControlPlaneClient controlPlaneClient,
                                      GrpcChannelFactory channelFactory,
                                      @Qualifier("transferExecutor") Executor transferExecutor,
                                      FiniteArtifactViewPrepareService finiteArtifactViewPrepareService) {
        this.sessionRegistry = sessionRegistry;
        this.sourceReaderRegistry = sourceReaderRegistry;
        this.grpcProperties = grpcProperties;
        this.sinkWriterRegistry = sinkWriterRegistry;
        this.controlPlaneClient = controlPlaneClient;
        this.channelFactory = channelFactory;
        this.transferExecutor = transferExecutor;
        this.finiteArtifactViewPrepareService = finiteArtifactViewPrepareService;
    }

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
     *   <li>Extracts CP-provided source S3 properties from the {@code source} metadata section
     *       (bucket, region, credentials, endpoint) and stores them in the session so
     *       {@link it.eng.dataplane.grpc.server.DataStreamService} can open the source without
     *       relying on local {@code s3.bucketName} configuration.</li>
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
        if (finiteArtifactViewPrepareService.isViewRequest(message)) {
            return finiteArtifactViewPrepareService.prepareViewResponse(PROTOCOL_ID, message);
        }

        DataFlowPrepareMetadataSection sourceSection = DataFlowPrepareMetadata.from(message).getSourceSection();
        String requestedSourceType = sourceSection.getString(DataPlaneConstants.METADATA_FIELD_SOURCE_TYPE);
        String sourceType = requestedSourceType == null ? DEFAULT_SOURCE_TYPE : requestedSourceType;
        sourceReaderRegistry.getReader(sourceType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No SourceReader available for sourceType: " + sourceType));

        boolean finite = !"false".equalsIgnoreCase(sourceSection.getString(DataPlaneConstants.METADATA_FIELD_FINITE));
        String mode = finite ? MODE_FINITE : MODE_NON_FINITE;

        Map<String, String> sourceProperties = buildSourcePropertiesFromMetadata(sourceSection, message.getDatasetId());

        String sessionId = UUID.randomUUID().toString();
        GrpcStreamSession session = GrpcStreamSession.Builder.newInstance()
                .sessionId(sessionId)
                .processId(message.getProcessId())
                .datasetId(message.getDatasetId())
                .finite(finite)
                .tenantId(message.getParticipantId())
                .sourceType(sourceType)
                .sourceProperties(sourceProperties)
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
     * <p>Acts as the consumer-side start flow: connects to the provider gRPC endpoint,
     * streams bytes into the configured sink, and emits DPS lifecycle callbacks.</p>
     *
     * @param dataFlow the data flow to initiate
     * @return future with the transfer result
     */
    @Override
    public CompletableFuture<DataFlowResult> initiateTransfer(DataFlow dataFlow) {
        Map<String, String> dataAddress = dataFlow.getDataAddress();
        if (dataAddress == null) {
            return CompletableFuture.completedFuture(DataFlowResult.failure("dataAddress is required for stream:grpc"));
        }

        String host = dataAddress.get(HOST_KEY);
        String portValue = dataAddress.get(PORT_KEY);
        String sessionId = dataAddress.get(SESSION_ID_KEY);
        String mode = dataAddress.getOrDefault(MODE_KEY, MODE_FINITE);
        if (isBlank(host) || isBlank(portValue) || isBlank(sessionId)) {
            return CompletableFuture.completedFuture(DataFlowResult.failure(
                    "Missing transport metadata: host, port, sessionId required for stream:grpc"));
        }

        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException exception) {
            return CompletableFuture.completedFuture(DataFlowResult.failure("Invalid port value: " + portValue));
        }

        Optional<SinkWriter> sinkWriterOptional = sinkWriterRegistry.getWriter(DEFAULT_SOURCE_TYPE);
        if (sinkWriterOptional.isEmpty()) {
            return CompletableFuture.completedFuture(DataFlowResult.failure(
                    "No SinkWriter available for type: " + DEFAULT_SOURCE_TYPE));
        }

        boolean finite = !MODE_NON_FINITE.equals(mode);
        String processId = dataFlow.getProcessId();
        String dataFlowId = dataFlow.getDataFlowId();
        CompletableFuture<DataFlowResult> future = new CompletableFuture<>();
        activeTransfers.put(dataFlowId, future);

        transferExecutor.execute(() -> executeTransfer(
                dataFlow,
                dataAddress,
                host,
                port,
                sessionId,
                finite,
                processId,
                dataFlowId,
                sinkWriterOptional.get(),
                future
        ));
        return future;
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
     * @param dataFlowId the data flow ID whose session should be released
     * @return future with a success result
     */
    @Override
    public CompletableFuture<DataFlowResult> terminateTransfer(String dataFlowId) {
        log.info("Terminating stream:grpc transfer dataFlowId={}", dataFlowId);
        CompletableFuture<DataFlowResult> pending = activeTransfers.remove(dataFlowId);
        if (pending != null && !pending.isDone()) {
            pending.complete(DataFlowResult.failure("transfer terminated"));
        }
        sessionRegistry.removeByProcessId(dataFlowId);
        ManagedChannel channel = activeChannels.remove(dataFlowId);
        if (channel != null) {
            channel.shutdownNow();
        }
        return CompletableFuture.completedFuture(DataFlowResult.success());
    }

    private void executeTransfer(DataFlow dataFlow,
                                 Map<String, String> dataAddress,
                                 String host,
                                 int port,
                                 String sessionId,
                                 boolean finite,
                                 String processId,
                                 String dataFlowId,
                                 SinkWriter sinkWriter,
                                 CompletableFuture<DataFlowResult> future) {
        ManagedChannel channel = channelFactory.create(host, port);
        activeChannels.put(dataFlowId, channel);
        try {
            DataStreamGrpc.DataStreamBlockingStub stub = DataStreamGrpc.newBlockingStub(channel);
            StreamRequest request = StreamRequest.newBuilder().setSessionId(sessionId).build();
            Iterator<DataChunk> chunks = stub.stream(request);

            sendStartedSafely(dataFlow.getCallbackAddress(), processId, dataAddress);
            log.info("Starting gRPC consumer stream sessionId={} processId={} finite={}",
                    sessionId, processId, finite);

            SinkContext sinkContext = buildSinkContext(dataFlow);
            try (GrpcChunkInputStream grpcStream = new GrpcChunkInputStream(chunks)) {
                SinkWriteResult writeResult = sinkWriter.write(grpcStream, sinkContext);
                if (writeResult.isSuccess()) {
                    if (finite) {
                        sendCompletedSafely(dataFlow.getCallbackAddress(), processId, dataAddress);
                        future.complete(DataFlowResult.success());
                    } else {
                        handleStreamError(dataFlow, "server closed non-finite stream unexpectedly", future);
                    }
                } else {
                    handleStreamError(dataFlow, writeResult.getErrorMessage(), future);
                }
            } catch (IOException exception) {
                handleStreamError(dataFlow, exception.getMessage(), future);
            }
        } catch (RuntimeException exception) {
            handleStreamError(dataFlow, exception.getMessage(), future);
        } finally {
            activeChannels.remove(dataFlowId);
            activeTransfers.remove(dataFlowId);
            channel.shutdownNow();
        }
    }

    private void sendStartedSafely(String callbackAddress, String processId, Map<String, String> dataAddress) {
        try {
            controlPlaneClient.sendStarted(callbackAddress, processId, dataAddress);
        } catch (RuntimeException exception) {
            log.warn("Failed to send started callback for processId={}: {}", processId, exception.getMessage());
        }
    }

    private void sendCompletedSafely(String callbackAddress, String processId, Map<String, String> dataAddress) {
        try {
            controlPlaneClient.sendCompleted(callbackAddress, processId, dataAddress);
        } catch (RuntimeException exception) {
            log.warn("Failed to send completed callback for processId={}: {}", processId, exception.getMessage());
        }
    }

    private void handleStreamError(DataFlow dataFlow,
                                   String errorMessage,
                                   CompletableFuture<DataFlowResult> future) {
        if (future.isDone()) {
            return;
        }
        String message = isBlank(errorMessage) ? "gRPC stream transfer failed" : errorMessage;
        log.error("gRPC stream error for processId={}: {}", dataFlow.getProcessId(), message);
        try {
            controlPlaneClient.sendErrored(dataFlow.getCallbackAddress(), dataFlow.getProcessId(), message);
        } catch (RuntimeException exception) {
            log.warn("Failed to send errored callback for processId={}: {}",
                    dataFlow.getProcessId(), exception.getMessage());
        }
        future.complete(DataFlowResult.failure(message));
    }

    private SinkContext buildSinkContext(DataFlow dataFlow) {
        DataFlowPrepareMetadataSection s3Section = DataFlowPrepareMetadata.fromMap(dataFlow.getMetadata())
                .getSinkSection()
                .getSection(DataPlaneConstants.METADATA_SECTION_S3);
        Map<String, String> sinkProperties = new HashMap<>();
        putIfPresent(sinkProperties, S3Utils.BUCKET_NAME,
                s3Section.getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        String objectKey = s3Section.getString(DataPlaneConstants.METADATA_S3_OBJECT_KEY);
        sinkProperties.put(S3Utils.OBJECT_KEY, objectKey != null ? objectKey : dataFlow.getProcessId());
        putIfPresent(sinkProperties, S3Utils.REGION,
                s3Section.getString(DataPlaneConstants.METADATA_S3_REGION));
        putIfPresent(sinkProperties, S3Utils.ACCESS_KEY,
                s3Section.getString(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        putIfPresent(sinkProperties, S3Utils.SECRET_KEY,
                s3Section.getString(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        putIfPresent(sinkProperties, S3Utils.ENDPOINT_OVERRIDE,
                s3Section.getString(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
        return SinkContext.Builder.newInstance()
                .properties(sinkProperties)
                .build();
    }

    private Map<String, String> buildSourcePropertiesFromMetadata(
            DataFlowPrepareMetadataSection sourceSection, String fallbackObjectKey) {
        DataFlowPrepareMetadataSection s3Section = sourceSection.getSection(DataPlaneConstants.METADATA_SECTION_S3);
        Map<String, String> props = new HashMap<>();
        putIfPresent(props, S3Utils.BUCKET_NAME,
                s3Section.getString(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        String objectKey = s3Section.getString(DataPlaneConstants.METADATA_S3_OBJECT_KEY);
        props.put(S3Utils.OBJECT_KEY, objectKey != null ? objectKey : fallbackObjectKey);
        putIfPresent(props, S3Utils.REGION,
                s3Section.getString(DataPlaneConstants.METADATA_S3_REGION));
        putIfPresent(props, S3Utils.ACCESS_KEY,
                s3Section.getString(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        putIfPresent(props, S3Utils.SECRET_KEY,
                s3Section.getString(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        putIfPresent(props, S3Utils.ENDPOINT_OVERRIDE,
                s3Section.getString(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE));
        return props;
    }

    private void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
