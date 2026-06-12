package it.eng.datatransfer.service.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.DataPlaneConstants;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.datatransfer.client.DataPlaneClient;
import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.rest.protocol.DataTransferCallback;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.TransportProfileResolver;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.model.BucketCredentialsEntity;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.BucketCredentialsService;
import it.eng.tools.s3.util.S3Utils;
import it.eng.tools.serializer.ToolsSerializer;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantBucketResolver;
import it.eng.tools.service.TenantContextHolder;
import it.eng.tools.usagecontrol.UsageControlProperties;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class DataTransferAPIService {

    private final TransferProcessRepository transferProcessRepository;
    private final OkHttpRestClient okHttpRestClient;
    private final CredentialUtils credentialUtils;
    private final DataTransferProperties dataTransferProperties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final UsageControlProperties usageControlProperties;
    private final AuditEventPublisher publisher;
    private final ArtifactTransferService artifactTransferService;
    private final DataPlaneClient dataPlaneClient;
    private final BucketCredentialsService bucketCredentialsService;
    private final TenantBucketResolver tenantBucketResolver;
    private final S3Properties s3Properties;
    private final TransportProfileResolver transportProfileResolver;

    /**
     * Creates a new {@code DataTransferAPIService}.
     *
     * @param transferProcessRepository  repository for transfer processes
     * @param okHttpRestClient           HTTP client for protocol messages
     * @param credentialUtils            connector credential provider
     * @param dataTransferProperties     configuration properties
     * @param usageControlProperties     usage control configuration
     * @param publisher                  audit event publisher
     * @param artifactTransferService    artifact lookup service
     * @param dataPlaneClient            DPS client for forwarding data-flow messages
     * @param bucketCredentialsService   service for resolving decrypted per-bucket S3 credentials
     * @param tenantBucketResolver       resolves the effective bucket name for the current tenant
     * @param s3Properties               S3 configuration (region, external endpoint)
     * @param transportProfileResolver   resolves the internal transport profile from a transfer format
     */
    public DataTransferAPIService(TransferProcessRepository transferProcessRepository,
                                  OkHttpRestClient okHttpRestClient,
                                  CredentialUtils credentialUtils,
                                  DataTransferProperties dataTransferProperties,
                                  UsageControlProperties usageControlProperties,
                                  AuditEventPublisher publisher,
                                  ArtifactTransferService artifactTransferService,
                                  DataPlaneClient dataPlaneClient,
                                  BucketCredentialsService bucketCredentialsService,
                                  TenantBucketResolver tenantBucketResolver,
                                  S3Properties s3Properties,
                                  TransportProfileResolver transportProfileResolver) {
        super();
        this.transferProcessRepository = transferProcessRepository;
        this.okHttpRestClient = okHttpRestClient;
        this.credentialUtils = credentialUtils;
        this.dataTransferProperties = dataTransferProperties;
        this.usageControlProperties = usageControlProperties;
        this.publisher = publisher;
        this.artifactTransferService = artifactTransferService;
        this.dataPlaneClient = dataPlaneClient;
        this.bucketCredentialsService = bucketCredentialsService;
        this.tenantBucketResolver = tenantBucketResolver;
        this.s3Properties = s3Properties;
        this.transportProfileResolver = transportProfileResolver;
    }

    /**
     * Resets any {@code isDownloadInProgress=true} flags left over from a previous crash or unclean shutdown.
     * Called automatically once the Spring context has finished initializing.
     */
    @PostConstruct
    void resetStaleDownloadingFlags() {
//TODO remove or move when the suspend/resume logic is in place. Check also if cleanup is still needed for stale parts on S3 (download never finished) .
        List<TransferProcess> stale = transferProcessRepository.findAllByIsDownloadInProgressTrue();
        if (!stale.isEmpty()) {
            log.warn("Found {} transfer process(es) with stale isDownloadInProgress=true flag. Resetting on startup.", stale.size());
            stale.forEach(tp -> {
                TransferProcess reset = tp.withIsDownloadInProgress(false);
                transferProcessRepository.save(reset);
                log.info("Reset isDownloadInProgress flag for transfer process {}", tp.getId());
            });
        }
    }

    /**
     * Find dataTransfer based on generic filter criteria.
     * Supports any field with automatic type detection and conversion.
     *
     * @param filters  Map of field names to filter values. All values are pre-validated and converted.
     * @param pageable Pageable
     * @return page of TransferProcess
     */
    public Page<TransferProcess> findDataTransfers(Map<String, Object> filters, Pageable pageable) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            filters = new HashMap<>(filters);
            filters.put("tenantId", tenantId);
        }
        return transferProcessRepository.findWithDynamicFilters(filters, TransferProcess.class, pageable);
    }

    /*###### CONSUMER #########*/

    /**
     * Request transfer service method.<br>
     * Check if state transition is OK; sends TransferRequestMessage to provider and based on response update state to REQUESTED.
     *
     * @param dataTransferRequest DataTransferRequest object containing transferProcessId, format and dataAddress
     * @return JsonNode representation of DataTransfer (should be requested if all OK)
     */
    public JsonNode requestTransfer(DataTransferRequest dataTransferRequest) {
        TransferProcess transferProcessInitialized = findTransferProcessById(dataTransferRequest.getTransferProcessId());

        stateTransitionCheck(TransferState.REQUESTED, transferProcessInitialized);
        DataAddress dataAddressForMessage = null;
        boolean isHttpPush = DataTransferFormat.HTTP_PUSH.format().equals(dataTransferRequest.getFormat());
        String assignedEndpoint = null;
        if (isHttpPush) {
            try {
                DataFlowPrepareMessage prepareMessage = applyCommonDataPlaneFields(
                        DataFlowPrepareMessage.Builder.newInstance(),
                        transferProcessInitialized,
                        DataTransferFormat.HTTP_PUSH.format())
                        .processId(transferProcessInitialized.getId())
                        .agreementId(transferProcessInitialized.getAgreementId())
                        .datasetId(transferProcessInitialized.getDatasetId())
                        .callbackAddress(dataTransferProperties.dataPlaneFeedbackAddress())
                        .metadata(buildHttpPushPrepareMetadata(
                                transferProcessInitialized.getTenantId(),
                                transferProcessInitialized.getId()))
                        .build();
                DataFlowPrepareResponse prepareResponse = dataPlaneClient.prepare(
                        prepareMessage,
                        DataTransferFormat.HTTP_PUSH.format(),
                        null);
                dataAddressForMessage = prepareResponseToDataAddress(
                        prepareResponse.getDataAddress(),
                        DataTransferFormat.HTTP_PUSH.format());
                assignedEndpoint = dataPlaneClient.getStickyEndpoint(transferProcessInitialized.getId()).orElse(null);
            } catch (Exception e) {
                assignedEndpoint = dataPlaneClient.getStickyEndpoint(transferProcessInitialized.getId()).orElse(null);
                cleanupPreparedDataPlaneSession(
                        transferProcessInitialized.getId(),
                        DataTransferFormat.HTTP_PUSH.format(),
                        null,
                        assignedEndpoint,
                        "HTTP-PUSH prepare cleanup failed for process");
                log.error("Failed to prepare HTTP-PUSH dataplane for transfer {}: {}",
                        transferProcessInitialized.getId(), e.getMessage());
                TransferProcess terminated = transferProcessInitialized.copyWithNewTransferState(TransferState.TERMINATED);
                transferProcessRepository.save(terminated);
                return TransferSerializer.serializePlainJsonNode(terminated);
            }
        } else {
            // For gRPC and other transport-profile formats: if the caller supplied source hints
            // (e.g. sourceType, finite) via dataAddress, pass them through to the provider so
            // the provider DP can configure the source reader correctly.
            // HTTP-PULL intentionally omits the dataAddress (presigned URL is generated by
            // the provider CP in startTransfer, not supplied by the consumer).
            String requestTransportProfile = transportProfileResolver.resolve(dataTransferRequest.getFormat());
            if (requestTransportProfile != null && dataTransferRequest.getDataAddress() != null) {
                try {
                    dataAddressForMessage = mapper.treeToValue(dataTransferRequest.getDataAddress(), DataAddress.class);
                } catch (Exception e) {
                    throw new DataTransferAPIException("Invalid dataAddress for "
                            + dataTransferRequest.getFormat() + " transfer request", e);
                }
            }
        }

        TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
                .agreementId(transferProcessInitialized.getAgreementId())
                .callbackAddress(dataTransferProperties.consumerCallbackAddress())
                .consumerPid(transferProcessInitialized.getConsumerPid())
                .format(dataTransferRequest.getFormat())
                .dataAddress(dataAddressForMessage)
                .build();

        GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
                DataTransferCallback.getConsumerDataTransferRequest(transferProcessInitialized.getCallbackAddress()),
                TransferSerializer.serializeProtocolJsonNode(transferRequestMessage),
                credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);

        TransferProcess transferProcessForDB;
        if (response.isSuccess()) {
            try {
                JsonNode jsonNode = mapper.readTree(response.getData());
                TransferProcess transferProcessFromResponse = TransferSerializer.deserializeProtocol(jsonNode, TransferProcess.class);

                transferProcessForDB = TransferProcess.Builder.newInstance()
                        .id(transferProcessInitialized.getId())
                        .agreementId(transferProcessInitialized.getAgreementId())
                        .consumerPid(transferProcessInitialized.getConsumerPid())
                        .providerPid(transferProcessFromResponse.getProviderPid())
                        .format(dataTransferRequest.getFormat())
                        .dataAddress(dataAddressForMessage)
                        .isDownloaded(transferProcessInitialized.isDownloaded())
                        .dataId(transferProcessInitialized.getDataId())
                        .callbackAddress(transferProcessInitialized.getCallbackAddress())
                        .role(IConstants.ROLE_CONSUMER)
                        .state(transferProcessFromResponse.getState())
                        .tenantId(transferProcessInitialized.getTenantId())
                        .assignedDataplaneEndpoint(assignedEndpoint)
                        .created(transferProcessInitialized.getCreated())
                        .createdBy(transferProcessInitialized.getCreatedBy())
                        .modified(transferProcessInitialized.getModified())
                        .lastModifiedBy(transferProcessInitialized.getLastModifiedBy())
                        .version(transferProcessInitialized.getVersion())
                        // although not needed on consumer side it is added here to avoid duplicate id exception from mongodb
                        .datasetId(transferProcessInitialized.getDatasetId())
                        .build();

                transferProcessRepository.save(transferProcessForDB);
                log.info("Transfer process {} saved", transferProcessForDB.getId());
                publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_REQUESTED,
                        "Transfer process requested successfully",
                        auditMap("transferProcess", transferProcessForDB,
                                "role", IConstants.ROLE_API,
                                "consumerPid", transferProcessForDB.getConsumerPid(),
                                "providerPid", transferProcessForDB.getProviderPid()));
            } catch (JsonProcessingException e) {
                log.error("Transfer process from response not valid");
                throw new DataTransferAPIException(e.getLocalizedMessage(), e);
            }
        } else {
            log.info("Error response received!");
            log.error("Transfer process from response not valid");
            JsonNode jsonNode;
            try {
                jsonNode = mapper.readTree(response.getData());
                try {
                    TransferError transferError = TransferSerializer.deserializeProtocol(jsonNode, TransferError.class);
                    Map<String, Object> details = new HashMap<>();
                    details.put("transferProcess", transferProcessInitialized);
                    details.put("role", IConstants.ROLE_API);
                    details.put("errorMessage", transferError);
                    if (transferProcessInitialized.getConsumerPid() != null) {
                        details.put("consumerPid", transferProcessInitialized.getConsumerPid());
                    }
                    if (transferProcessInitialized.getProviderPid() != null) {
                        details.put("providerPid", transferProcessInitialized.getProviderPid());
                    }
                    publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_REQUESTED,
                            "Transfer process request failed",
                            details);
                    if (isHttpPush) {
                        cleanupPreparedDataPlaneSession(
                                transferProcessInitialized.getId(),
                                DataTransferFormat.HTTP_PUSH.format(),
                                null,
                                assignedEndpoint,
                                "HTTP-PUSH request cleanup failed for process");
                    }
                    throw new DataTransferAPIException(transferError, "Error making request");
                } catch (jakarta.validation.ValidationException ve) {
                    log.warn("Provider error response is not a DSP TransferError: {}", response.getData());
                    if (isHttpPush) {
                        cleanupPreparedDataPlaneSession(
                                transferProcessInitialized.getId(),
                                DataTransferFormat.HTTP_PUSH.format(),
                                null,
                                assignedEndpoint,
                                "HTTP-PUSH request cleanup failed for process");
                    }
                    throw new DataTransferAPIException("Transfer request failed: " + response.getMessage());
                }
            } catch (JsonProcessingException ex) {
                if (isHttpPush) {
                    cleanupPreparedDataPlaneSession(
                            transferProcessInitialized.getId(),
                            DataTransferFormat.HTTP_PUSH.format(),
                            null,
                            assignedEndpoint,
                            "HTTP-PUSH request cleanup failed for process");
                }
                throw new DataTransferAPIException("Error occurred");
            }
        }
        return TransferSerializer.serializePlainJsonNode(transferProcessForDB);
    }

    /**
     * Sends TransferStartMessage.
     * Updates state for Transfer Process upon successful response to STARTED
     *
     * @param transferProcessId transfer process id
     * @return JsonNode representation of DataTransfer
     */
    public JsonNode startTransfer(String transferProcessId) {
        TransferProcess transferProcess = findTransferProcessById(transferProcessId);

        if (Strings.CS.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole()) && TransferState.REQUESTED.equals(transferProcess.getState())) {
            throw new DataTransferAPIException("State transition aborted, consumer can not transit from " + transferProcess.getState().name()
                    + " to " + TransferState.STARTED.name());
        }

        stateTransitionCheck(TransferState.STARTED, transferProcess);

        log.info("Sending TransferStartMessage to {}", transferProcess.getCallbackAddress());
        String address = null;
        DataAddress dataAddress = null;
        // Transport profile and sticky endpoint for provider-side gRPC prepare.
        // Both are null for HTTP-PULL/PUSH and get populated in the provider+gRPC branch below.
        String transportProfile = null;
        String assignedEndpoint = null;

        if (Strings.CS.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
            address = DataTransferCallback.getProviderDataTransferStart(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
        }
        if (Strings.CS.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
            address = DataTransferCallback.getConsumerDataTransferStart(transferProcess.getCallbackAddress(), transferProcess.getConsumerPid());
            transportProfile = transportProfileResolver.resolve(transferProcess.getFormat());
            if (transportProfile != null) {
                // gRPC (or other profile-based transport): call DPS prepare so the provider DP
                // opens a session and returns the endpoint metadata the consumer needs.
                // Forward any stored request-side source hints (e.g. sourceType, finite)
                // in the structured prepare metadata so the DP can configure the source correctly.
                DataFlowPrepareMessage prepareMessage = applyCommonDataPlaneFields(DataFlowPrepareMessage.Builder.newInstance(),
                                transferProcess, transportProfile)
                        .processId(transferProcess.getId())
                        .agreementId(transferProcess.getAgreementId())
                        .datasetId(transferProcess.getDatasetId())
                        .callbackAddress(dataTransferProperties.dataPlaneFeedbackAddress())
                        .metadata(buildPrepareMetadata(transferProcess.getTenantId(),
                                transferProcess.getDatasetId(), transferProcess.getDataAddress()))
                        .build();
                DataFlowPrepareResponse prepareResponse = dataPlaneClient.prepare(prepareMessage,
                        transferProcess.getFormat(), transportProfile);
                // The prepared address (gRPC endpoint metadata) becomes the DataAddress in the
                // TransferStartMessage so the consumer receives the session details it needs.
                if (prepareResponse != null && prepareResponse.getDataAddress() != null) {
                    dataAddress = prepareResponseToDataAddress(prepareResponse.getDataAddress(), transportProfile);
                }
                // Capture the selected DP endpoint for persistence (restart-safe sticky routing).
                assignedEndpoint = dataPlaneClient.getStickyEndpoint(transferProcess.getId()).orElse(null);
                if (dataAddress == null) {
                    cleanupPreparedDataPlaneSession(transferProcess.getId(), transferProcess.getFormat(),
                            transportProfile, assignedEndpoint,
                            "gRPC prepare returned no dataAddress");
                    throw new DataTransferAPIException("gRPC prepare returned no dataAddress");
                }
            } else if ("HttpData-PULL".equals(transferProcess.getFormat())) {
                Artifact artifact = artifactTransferService.findArtifact(transferProcess);
                if (artifact.getArtifactType() == ArtifactType.FILE) {
                    // FILE: delegate presigned URL generation to the provider Data Plane so the DP
                    // can use its own S3 credentials and presigning infrastructure.
                    DataFlowPrepareMessage prepareMsg = applyCommonDataPlaneFields(
                                    DataFlowPrepareMessage.Builder.newInstance(), transferProcess, transferProcess.getFormat())
                            .processId(transferProcess.getId())
                            .agreementId(transferProcess.getAgreementId())
                            .datasetId(transferProcess.getDatasetId())
                            .callbackAddress(dataTransferProperties.dataPlaneFeedbackAddress())
                            .metadata(buildPrepareMetadata(transferProcess.getTenantId(),
                                    transferProcess.getDatasetId(), transferProcess.getDataAddress()))
                            .build();
                    DataFlowPrepareResponse prepareResponse;
                    try {
                        prepareResponse = dataPlaneClient.prepare(prepareMsg, "HttpData-PULL", null);
                    } catch (Exception prepareEx) {
                        assignedEndpoint = dataPlaneClient.getStickyEndpoint(transferProcess.getId()).orElse(null);
                        cleanupPreparedDataPlaneSession(transferProcess.getId(), "HttpData-PULL", null, assignedEndpoint,
                                "HTTP-PULL FILE DP prepare failed (best-effort cleanup)");
                        throw new DataTransferAPIException("HTTP-PULL DP prepare failed: " + prepareEx.getMessage());
                    }
                    // Capture sticky immediately so it is available for cleanup if the response is unusable.
                    assignedEndpoint = dataPlaneClient.getStickyEndpoint(transferProcess.getId()).orElse(null);
                    if (prepareResponse == null || prepareResponse.getDataAddress() == null) {
                        cleanupPreparedDataPlaneSession(transferProcess.getId(), "HttpData-PULL", null, assignedEndpoint,
                                "HTTP-PULL DP prepare returned no dataAddress");
                        throw new DataTransferAPIException("HTTP-PULL DP prepare returned no dataAddress");
                    }
                    dataAddress = prepareResponseToDataAddress(prepareResponse.getDataAddress(), "HttpData-PULL");
                } else {
                    // EXTERNAL: CP generates the callback URL directly
                    String transactionId = Base64.encodeBase64URLSafeString(
                            (transferProcess.getConsumerPid() + "|" + transferProcess.getProviderPid())
                                    .getBytes(StandardCharsets.UTF_8));
                    String artifactURL = DataTransferCallback.getValidCallback(dataTransferProperties.providerCallbackAddress())
                            + "/artifacts/" + transactionId;
                    EndpointProperty endpointProperty = EndpointProperty.Builder.newInstance()
                            .name("https://w3id.org/edc/v0.0.1/ns/endpoint")
                            .value(artifactURL)
                            .build();
                    EndpointProperty endpointTypeProperty = EndpointProperty.Builder.newInstance()
                            .name("https://w3id.org/edc/v0.0.1/ns/endpointType")
                            .value("https://w3id.org/idsa/v4.1/HTTP")
                            .build();
                    dataAddress = DataAddress.Builder.newInstance()
                            .endpoint(artifactURL)
                            .endpointProperties(List.of(endpointProperty, endpointTypeProperty))
                            .endpointType("https://w3id.org/idsa/v4.1/HTTP")
                            .build();
                }
            }
        }

        if (address == null) {
            throw new DataTransferAPIException("Cannot resolve callback address for unknown role: " + transferProcess.getRole());
        }

        // Any address set explicitly by a protocol-specific prepare step (gRPC, HTTP-PULL DP prepare,
        // EXTERNAL URL generation) must win over the stored request-side DataAddress, because the
        // consumer needs the session endpoint, presigned URL, or artifact URL — not the original
        // source configuration. For other cases (e.g. regular HTTP-PUSH) dataAddress stays null
        // and we fall back to the stored DataAddress.
        DataAddress finalDataAddress = dataAddress != null ? dataAddress : transferProcess.getDataAddress();

        TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
                .consumerPid(transferProcess.getConsumerPid())
                .providerPid(transferProcess.getProviderPid())
                .dataAddress(finalDataAddress)
                .build();

        // Save STARTED state BEFORE sending TransferStartMessage to the peer.
        // This eliminates a race condition where the peer (consumer) triggers a fast
        // auto-download and sends TransferCompletionMessage back to us before our
        // synchronous HTTP call returns and we would otherwise save STARTED. Without
        // this early save, the completion handler would find the TP still in REQUESTED
        // and reject the message with a 400 Bad Request.
        TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
                .id(transferProcess.getId())
                .agreementId(transferProcess.getAgreementId())
                .consumerPid(transferProcess.getConsumerPid())
                .providerPid(transferProcess.getProviderPid())
                .callbackAddress(transferProcess.getCallbackAddress())
                .dataAddress(transferStartMessage.getDataAddress())
                .isDownloaded(transferProcess.isDownloaded())
                .dataId(transferProcess.getDataId())
                .format(transferProcess.getFormat())
                .state(TransferState.STARTED)
                .role(transferProcess.getRole())
                .datasetId(transferProcess.getDatasetId())
                .tenantId(transferProcess.getTenantId())
                .transportProfile(transportProfile)
                .assignedDataplaneEndpoint(assignedEndpoint)
                .created(transferProcess.getCreated())
                .createdBy(transferProcess.getCreatedBy())
                .modified(transferProcess.getModified())
                .lastModifiedBy(transferProcess.getLastModifiedBy())
                .version(transferProcess.getVersion())
                .build();
        transferProcessStarted = transferProcessRepository.save(transferProcessStarted);
        log.info("Transfer process {} pre-saved as STARTED before notifying peer", transferProcessStarted.getId());

        GenericApiResponse<String> response = okHttpRestClient
                .sendRequestProtocol(address,
                        TransferSerializer.serializeProtocolJsonNode(transferStartMessage),
                        credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        if (response.isSuccess()) {
            // For HTTP-PULL FILE: the DP was used only as a helper to generate a presigned URL.
            // The PREPARED session has no ongoing data flow; terminate it so it does not accumulate.
            // gRPC sessions (transportProfile != null) are NOT terminated here — the DP hosts active streaming.
            if (transportProfile == null && assignedEndpoint != null) {
                cleanupPreparedDataPlaneSession(transferProcessStarted.getId(), transferProcessStarted.getFormat(),
                        null, assignedEndpoint,
                        "HTTP-PULL FILE DP terminate failed (best-effort)");
            }
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_STARTED,
                    "Transfer process started successfully",
                    auditMap("transferProcess", transferProcessStarted,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcessStarted.getConsumerPid(),
                            "providerPid", transferProcessStarted.getProviderPid()));
            return TransferSerializer.serializePlainJsonNode(transferProcessStarted);
        } else {
            log.error("Error response received — rolling back TP to REQUESTED state");
            // Roll back: restore the original REQUESTED state so the admin can retry.
            // Build the rollback entity from transferProcessStarted (which has the current @Version
            // after the STARTED save) to avoid OptimisticLockingFailureException. Use the original
            // dataAddress from transferProcess so generated fields (e.g. presigned URLs) are discarded.
            TransferProcess rollback = TransferProcess.Builder.newInstance()
                    .id(transferProcessStarted.getId())
                    .agreementId(transferProcessStarted.getAgreementId())
                    .consumerPid(transferProcessStarted.getConsumerPid())
                    .providerPid(transferProcessStarted.getProviderPid())
                    .callbackAddress(transferProcessStarted.getCallbackAddress())
                    .dataAddress(transferProcess.getDataAddress())
                    .isDownloaded(transferProcessStarted.isDownloaded())
                    .dataId(transferProcessStarted.getDataId())
                    .format(transferProcessStarted.getFormat())
                    .state(TransferState.REQUESTED)
                    .role(transferProcessStarted.getRole())
                    .datasetId(transferProcessStarted.getDatasetId())
                    .retryCount(transferProcessStarted.getRetryCount())
                    .tenantId(transferProcessStarted.getTenantId())
                    // Preserve routing metadata so that if the best-effort DP terminate below fails,
                    // later cleanup/recovery can still route back to the same DP instance.
                    .transportProfile(transportProfile)
                    .assignedDataplaneEndpoint(assignedEndpoint)
                    .created(transferProcessStarted.getCreated())
                    .createdBy(transferProcessStarted.getCreatedBy())
                    .modified(transferProcessStarted.getModified())
                    .lastModifiedBy(transferProcessStarted.getLastModifiedBy())
                    .version(transferProcessStarted.getVersion())
                    .build();
            transferProcessRepository.save(rollback);
            // Best-effort: if any DP session was prepared (gRPC, HTTP-PULL), terminate it and
            // clear the sticky assignment so the router does not retain a stale entry.
            // transportProfile is set for gRPC; assignedEndpoint can be non-null for HTTP-PULL
            // even though transportProfile is null.
            if (transportProfile != null || assignedEndpoint != null) {
                cleanupPreparedDataPlaneSession(transferProcessStarted.getId(), transferProcessStarted.getFormat(),
                        transportProfile, assignedEndpoint,
                        "Best-effort DP terminate failed for rolled-back process");
            }
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_STARTED,
                    "Transfer process start failed",
                    auditMap("transferProcess", transferProcess,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid(),
                            "errorMessage", response.getMessage()));
            throw new DataTransferAPIException(response.getMessage());
        }
    }

    /**
     * Converts a flat {@code dataAddress} map from a {@link DataFlowPrepareResponse} into a
     * {@link DataAddress} suitable for embedding in a DSP {@link TransferStartMessage}.
     *
     * <p>All map entries are stored as {@link EndpointProperty} instances. Entries with keys
     * {@code "endpoint"} and {@code "endpointType"} are also promoted to the corresponding
     * top-level fields so that standard DSP consumers can locate them.</p>
     *
     * @param responseMap the flat address map from the DP prepare response; must not be {@code null}
     * @param transferType the transport profile that produced the response
     * @return a {@link DataAddress} carrying the DP-returned addressing metadata
     */
    private DataAddress prepareResponseToDataAddress(Map<String, String> responseMap, String transferType) {
        List<EndpointProperty> props = responseMap.entrySet().stream()
                .map(e -> EndpointProperty.Builder.newInstance().name(e.getKey()).value(e.getValue()).build())
                .toList();
        DataAddress.Builder builder = DataAddress.Builder.newInstance().endpointProperties(props);
        String endpoint = responseMap.get(DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT);
        if (endpoint != null) {
            builder.endpoint(endpoint);
        }
        builder.endpointType(StringUtils.defaultIfBlank(
                responseMap.get(DataPlaneConstants.DATA_ADDRESS_FIELD_ENDPOINT_TYPE),
                defaultStartMessageEndpointType(transferType)));
        return builder.build();
    }

    /**
     * Builds the DPS prepare metadata for an HTTP-PUSH consumer request.
     *
     * <p>The sink section carries the consumer bucket coordinates plus Minio management
     * credentials from {@code application.properties}. This is a temporary fallback for Minio:
     * delegated temp-user CRUD through {@link BucketCredentialsEntity} is currently blocked by
     * Minio policy limitations in the tested environment. Once tenant-scoped bucket manager
     * policies work, replace these bootstrap credentials with the persisted
     * {@link BucketCredentialsEntity} values.</p>
     *
     * @param tenantId  the consumer tenant (used to resolve the per-tenant bucket name)
     * @param objectKey the S3 object key for the pushed artifact (transfer process id)
     * @return metadata map with {@code sink.s3} bucket/key coordinates but no credentials
     */
    private Map<String, Object> buildHttpPushPrepareMetadata(String tenantId, String objectKey) {
        return buildCanonicalMetadata(
                Map.of(),
                buildCanonicalSection(Map.of(), buildBootstrapManagementS3Metadata(tenantId, objectKey), null));
    }

    /**
     * Builds structured prepare-time metadata for DPS prepare calls.
     *
     * @param tenantId the tenant that owns the provider bucket
     * @param objectKey the source object key selected by the control plane
     * @param sourceDataAddress request-side source hints to place under the source section
     * @return structured prepare metadata
     */
    private Map<String, Object> buildPrepareMetadata(String tenantId,
                                                     String objectKey,
                                                     DataAddress sourceDataAddress) {
        return buildCanonicalMetadata(
                buildCanonicalSection(toStructuredSectionMetadata(sourceDataAddress),
                        buildControlPlaneS3Metadata(tenantId, objectKey),
                        null),
                Map.of());
    }

    private DataFlowPrepareMessage.Builder applyCommonDataPlaneFields(DataFlowPrepareMessage.Builder builder,
                                                                      TransferProcess transferProcess,
                                                                      String transferType) {
        return builder.messageId(UUID.randomUUID().toString())
                .participantId(resolveLocalParticipantId(transferProcess))
                .counterPartyId(resolveRemoteParticipantId(transferProcess))
                .dataspaceContext(DataPlaneConstants.DSPACE_2025_01_CONTEXT)
                .claims(Map.of())
                .transferType(transferType);
    }

    private DataFlowStartMessage.Builder applyCommonDataPlaneFields(DataFlowStartMessage.Builder builder,
                                                                    TransferProcess transferProcess,
                                                                    String transferType,
                                                                    Map<String, Object> metadata) {
        return builder.messageId(UUID.randomUUID().toString())
                .participantId(resolveLocalParticipantId(transferProcess))
                .counterPartyId(resolveRemoteParticipantId(transferProcess))
                .dataspaceContext(DataPlaneConstants.DSPACE_2025_01_CONTEXT)
                .claims(Map.of())
                .transferType(transferType)
                .metadata(metadata);
    }

    private String resolveLocalParticipantId(TransferProcess transferProcess) {
        if (Strings.CS.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
            return transferProcess.getProviderPid();
        }
        return transferProcess.getConsumerPid();
    }

    private String resolveRemoteParticipantId(TransferProcess transferProcess) {
        if (Strings.CS.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
            return transferProcess.getConsumerPid();
        }
        return transferProcess.getProviderPid();
    }

    private void cleanupPreparedDataPlaneSession(String processId,
                                                 String transferType,
                                                 String transportProfile,
                                                 String assignedEndpoint,
                                                 String failurePrefix) {
        if (assignedEndpoint != null) {
            dataPlaneClient.restoreStickyAssignment(processId, assignedEndpoint);
        }
        try {
            dataPlaneClient.terminate(processId, transferType, transportProfile);
        } catch (Exception e) {
            log.warn("{} {}: {}", failurePrefix, processId, e.getMessage());
        }
        dataPlaneClient.clearStickyAssignment(processId);
    }

    /**
     * Sends TransferCompletionMessage.<br>
     * Updates state for Transfer Process upon successful response to COMPLETED
     *
     * @param transferProcessId transfer process id
     * @return JsonNode representation of DataTransfer
     */
    public JsonNode completeTransfer(String transferProcessId) {
        TransferProcess transferProcess = findTransferProcessById(transferProcessId);

        stateTransitionCheck(TransferState.COMPLETED, transferProcess);

        TransferCompletionMessage transferCompletionMessage = TransferCompletionMessage.Builder.newInstance()
                .consumerPid(transferProcess.getConsumerPid())
                .providerPid(transferProcess.getProviderPid())
                .build();

        String address = null;
        if (Strings.CS.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
            address = DataTransferCallback.getProviderDataTransferCompletion(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
        }
        if (Strings.CS.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
            address = DataTransferCallback.getConsumerDataTransferCompletion(transferProcess.getCallbackAddress(), transferProcess.getConsumerPid());
        }
        log.info("Sending TransferCompletionMessage to {}", address);
        if (address == null) {
            throw new DataTransferAPIException("Cannot resolve callback address for unknown role: " + transferProcess.getRole());
        }

        GenericApiResponse<String> response = okHttpRestClient
                .sendRequestProtocol(address,
                        TransferSerializer.serializeProtocolJsonNode(transferCompletionMessage),
                        credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        if (response.isSuccess()) {
            boolean wasDownloading = transferProcess.isDownloadInProgress();
            TransferProcess transferProcessCompleted = transferProcess
                    .copyWithNewTransferState(TransferState.COMPLETED)
                    .withIsDownloadInProgress(false);
            if (wasDownloading) {
                // DP completed a download on behalf of this TP — mark it as downloaded.
                transferProcessCompleted = transferProcessCompleted
                        .withIsDownloaded(true)
                        .withDataId(transferProcess.getId());
            }
            transferProcessRepository.save(transferProcessCompleted);
            log.info("Transfer process {} saved", transferProcessCompleted.getId());
            // For HTTP-PUSH consumer transfers with an assigned DP endpoint, restore sticky routing
            // and signal the DP to terminate its PREPARED session — this triggers
            // HttpPushTransferProtocol.terminateTransfer() which owns the temp-IAM-user cleanup.
            if (IConstants.ROLE_CONSUMER.equals(transferProcess.getRole())
                    && DataTransferFormat.HTTP_PUSH.format().equals(transferProcess.getFormat())
                    && transferProcess.getAssignedDataplaneEndpoint() != null) {
                dataPlaneClient.restoreStickyAssignment(transferProcess.getId(),
                        transferProcess.getAssignedDataplaneEndpoint());
                try {
                    dataPlaneClient.terminate(transferProcess.getId(),
                            DataTransferFormat.HTTP_PUSH.format(), null);
                } catch (Exception e) {
                    log.warn("DP terminate call failed for HTTP-PUSH consumer process {} (best-effort): {}",
                            transferProcess.getId(), e.getMessage());
                }
            }
            dataPlaneClient.clearStickyAssignment(transferProcess.getId());
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_COMPLETED,
                    "Transfer process completed successfully",
                    auditMap("transferProcess", transferProcessCompleted,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcessCompleted.getConsumerPid(),
                            "providerPid", transferProcessCompleted.getProviderPid()));
            return TransferSerializer.serializePlainJsonNode(transferProcessCompleted);
        } else {
            log.error("Error response received!");
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_COMPLETED,
                    "Transfer process completion failed",
                    auditMap("transferProcess", transferProcess,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid(),
                            "errorMessage", response.getMessage()));
            throw new DataTransferAPIException(response.getMessage());
        }
    }

    /**
     * Sends TransferSuspensionMessage.<br>
     * Updates state for Transfer Process upon successful response to COMPLETED
     *
     * @param transferProcessId transfer process id
     * @return JsonNode representation of DataTransfer
     */
    public JsonNode suspendTransfer(String transferProcessId) {
        TransferProcess transferProcess = findTransferProcessById(transferProcessId);

        stateTransitionCheck(TransferState.SUSPENDED, transferProcess);

        if (transferProcess.isDownloadInProgress()) {
            log.error("Cannot suspend transfer {} while data transfer is in progress", transferProcessId);
            throw new DataTransferAPIException(
                    "Cannot suspend transfer while data transfer is in progress. "
                            + "The active data plane transfer cannot be paused mid-flight.");
        }

        TransferSuspensionMessage transferSuspensionMessage = TransferSuspensionMessage.Builder.newInstance()
                .consumerPid(transferProcess.getConsumerPid())
                .providerPid(transferProcess.getProviderPid())
                //TODO which code to add
                .code("200")
                .reason(List.of("Data transfer suspended"))
                .build();

        log.info("Sending TransferSuspensionMessage to {}", transferProcess.getCallbackAddress());
        String address = null;

        if (Strings.CS.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
            address = DataTransferCallback.getProviderDataTransferSuspension(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
        }
        if (Strings.CS.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
            address = DataTransferCallback.getConsumerDataTransferSuspension(transferProcess.getCallbackAddress(), transferProcess.getConsumerPid());
        }
        if (address == null) {
            throw new DataTransferAPIException("Cannot resolve callback address for unknown role: " + transferProcess.getRole());
        }
        GenericApiResponse<String> response = okHttpRestClient
                .sendRequestProtocol(address,
                        TransferSerializer.serializeProtocolJsonNode(transferSuspensionMessage),
                        credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        if (response.isSuccess()) {
            TransferProcess transferProcessStarted = transferProcess.copyWithNewTransferState(TransferState.SUSPENDED);
            transferProcessRepository.save(transferProcessStarted);
            log.info("Transfer process {} saved", transferProcessStarted.getId());
            if (transferProcess.getTransportProfile() != null) {
                // Restore sticky from persisted endpoint so the DP call reaches the right instance
                // even after a CP restart that cleared the in-memory stickyMap.
                if (transferProcess.getAssignedDataplaneEndpoint() != null) {
                    dataPlaneClient.restoreStickyAssignment(transferProcess.getId(),
                            transferProcess.getAssignedDataplaneEndpoint());
                }
                // CP is already SUSPENDED. A DP failure must be surfaced explicitly so the caller
                // knows CP/DP are inconsistent, while still publishing the audit trail.
                try {
                    dataPlaneClient.suspend(transferProcess.getId(), transferProcess.getFormat(),
                            transferProcess.getTransportProfile());
                } catch (Exception e) {
                    log.error("DP suspend call failed for process {}: {}", transferProcess.getId(), e.getMessage());
                    publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED,
                            "Transfer process suspension DP call failed",
                            auditMap("transferProcess", transferProcess,
                                    "role", IConstants.ROLE_API,
                                    "consumerPid", transferProcess.getConsumerPid(),
                                    "providerPid", transferProcess.getProviderPid(),
                                    "errorMessage", e.getMessage()));
                    throw new DataTransferAPIException(
                            "DP suspend call failed for process " + transferProcess.getId() + ": " + e.getMessage());
                }
            }
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED,
                    "Transfer process suspended successfully",
                    auditMap("transferProcess", transferProcess,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid()));
            return TransferSerializer.serializePlainJsonNode(transferProcessStarted);
        } else {
            log.error("Error response received!");
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED,
                    "Transfer process suspension failed",
                    auditMap("transferProcess", transferProcess,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid(),
                            "errorMessage", response.getMessage()));
            throw new DataTransferAPIException(response.getMessage());
        }
    }

    /**
     * Sends TransferTerminationMessage.<br>
     * Updates state for Transfer Process upon successful response to TERMINATED
     *
     * @param transferProcessId transfer process id
     * @return JsonNode representation of DataTransfer
     */
    public JsonNode terminateTransfer(String transferProcessId) {
        TransferProcess transferProcess = findTransferProcessById(transferProcessId);

        stateTransitionCheck(TransferState.TERMINATED, transferProcess);

        TransferTerminationMessage transferTerminationMessage = TransferTerminationMessage.Builder.newInstance()
                .consumerPid(transferProcess.getConsumerPid())
                .providerPid(transferProcess.getProviderPid())
                //TODO which code to add
                .code("200")
                .reason(List.of("Data transfer terminated"))
                .build();

        log.info("Sending TransferTerminationMessage to {}", transferProcess.getCallbackAddress());
        String address = null;

        if (Strings.CS.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
            address = DataTransferCallback.getProviderDataTransferTermination(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
        }
        if (Strings.CS.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
            address = DataTransferCallback.getConsumerDataTransferTermination(transferProcess.getCallbackAddress(), transferProcess.getConsumerPid());
        }
        if (address == null) {
            throw new DataTransferAPIException("Cannot resolve callback address for unknown role: " + transferProcess.getRole());
        }
        GenericApiResponse<String> response = okHttpRestClient
                .sendRequestProtocol(address,
                        TransferSerializer.serializeProtocolJsonNode(transferTerminationMessage),
                        credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        if (response.isSuccess()) {
            TransferProcess transferProcessStarted = transferProcess.copyWithNewTransferState(TransferState.TERMINATED);
            transferProcessRepository.save(transferProcessStarted);
            log.info("Transfer process {} saved", transferProcessStarted.getId());
            if (transferProcess.getTransportProfile() != null || transferProcess.getAssignedDataplaneEndpoint() != null) {
                // Restore sticky from persisted endpoint so the DP call reaches the right instance
                // even after a CP restart that cleared the in-memory stickyMap.
                if (transferProcess.getAssignedDataplaneEndpoint() != null) {
                    dataPlaneClient.restoreStickyAssignment(transferProcess.getId(),
                            transferProcess.getAssignedDataplaneEndpoint());
                }
                // Best-effort: CP is already TERMINATED. A DP failure must not prevent sticky cleanup.
                try {
                    dataPlaneClient.terminate(transferProcess.getId(), transferProcess.getFormat(),
                            transferProcess.getTransportProfile());
                } catch (Exception e) {
                    log.warn("DP terminate call failed for process {} (best-effort); sticky cleanup will still run: {}",
                            transferProcess.getId(), e.getMessage());
                }
            }
            dataPlaneClient.clearStickyAssignment(transferProcess.getId());
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_TERMINATED,
                    "Transfer process terminated successfully",
                    auditMap("transferProcess", transferProcess,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid()));
            return TransferSerializer.serializePlainJsonNode(transferProcessStarted);
        } else {
            log.error("Error response received!");
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_TERMINATED,
                    "Transfer process termination failed",
                    auditMap("transferProcess", transferProcess,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid(),
                            "errorMessage", response.getMessage()));
            throw new DataTransferAPIException(response.getMessage());
        }
    }

    /**
     * Download data.<br>
     * Checks if TransferProcess state is STARTED; enforce policy (validate agreement); download data from provider;
     * store artifact in S3; update Transfer Process downloaded to true
     *
     * @param transferProcessId transfer process id
     * @return CompletableFuture<Void> that completes when the download is finished
     */
    public CompletableFuture<Void> downloadData(String transferProcessId) {
        TransferProcess transferProcess = findTransferProcessById(transferProcessId);

        if (!transferProcess.getState().equals(TransferState.STARTED)) {
            log.error("Download aborted, Transfer Process is not in STARTED state");
            // Throw synchronously so the exception propagates to the HTTP layer and returns 400.
            throw new DataTransferAPIException("Download aborted, Transfer Process is not in STARTED state");
        }

        if (transferProcess.isDownloaded()) {
            log.error("Download aborted, data for Transfer Process {} has already been downloaded", transferProcessId);
            // Throw synchronously so the exception propagates to the HTTP layer and returns 400.
            throw new DataTransferAPIException("Download aborted, data for Transfer Process " + transferProcessId + " has already been downloaded");
        }

        if (transferProcess.isDownloadInProgress()) {
            log.error("Download aborted, Transfer Process {} is already in progress", transferProcessId);
            // Throw synchronously so the exception propagates to the HTTP layer and returns 400.
            throw new DataTransferAPIException("Download aborted, Transfer Process " + transferProcessId + " is already in progress");
        }

        // Mark download as in progress and persist so the frontend spinner can react.
        // Resolve and persist the transport profile so that sticky lifecycle calls
        // (terminate, suspend) can reach the same Data Plane instance later.
        // The @Version field provides optimistic locking: a concurrent request that also
        // passed the isDownloadInProgress check above will fail here with OptimisticLockingFailureException.
        String transportProfile = transportProfileResolver.resolve(transferProcess.getFormat());
        TransferProcess withFlags = transferProcess.withIsDownloadInProgress(true);
        if (transportProfile != null) {
            withFlags = withFlags.withTransportProfile(transportProfile);
        }
        TransferProcess transferProcessDownloading;
        try {
            transferProcessDownloading = transferProcessRepository.save(withFlags);
        } catch (OptimisticLockingFailureException e) {
            log.error("Download aborted, Transfer Process {} is already in progress (concurrent request)", transferProcessId);
            throw new DataTransferAPIException("Download aborted, Transfer Process " + transferProcessId + " is already in progress");
        }

        try {
            policyCheck(transferProcessDownloading);
        } catch (DataTransferAPIException e) {
            transferProcessRepository.save(transferProcessDownloading.withIsDownloadInProgress(false));
            return CompletableFuture.failedFuture(e);
        }
        log.info("Starting download transfer process id - {} data...", transferProcessId);

        // Dispatch to the external Data Plane microservice via DPS.
        // The data plane performs the actual transfer asynchronously and POSTs back to
        // DataFlowCallbackController when done. Completion/termination lifecycle is driven
        // entirely by those callbacks — do NOT call completeTransfer() here.
        try {
            // Use dataPlaneFeedbackAddress() (global base URL, no tenant path) so the Data Plane
            // can POST back to /api/v1/dataflows/complete on this connector's admin chain.
            // providerCallbackAddress() would include the tenant path which the admin
            // DataFlowCallbackController does not have, causing a 404.
            String callbackAddress = dataTransferProperties.dataPlaneFeedbackAddress();
            DataFlowStartMessage startMessage = applyCommonDataPlaneFields(DataFlowStartMessage.Builder.newInstance(),
                            transferProcessDownloading,
                            transferProcessDownloading.getFormat(),
                            buildStartMetadata(transferProcessDownloading))
                    .processId(transferProcessDownloading.getId())
                    .callbackAddress(callbackAddress)
                    .dataAddress(buildStartMessageDataAddress(transferProcessDownloading))
                    .agreementId(transferProcessDownloading.getAgreementId())
                    .datasetId(transferProcessDownloading.getDatasetId())
                    .build();
            if (transportProfile != null) {
                dataPlaneClient.start(startMessage, transportProfile);
            } else {
                dataPlaneClient.start(startMessage, null);
            }
            // Persist the selected DP endpoint for both profile-based and HTTP transports
            // so sticky routing survives a CP restart.
            // If the DP completes very quickly and its callback already advanced the TP version,
            // this secondary save will lose the optimistic-lock race. That is benign: the transfer
            // succeeded and the endpoint no longer matters for routing.
            dataPlaneClient.getStickyEndpoint(transferProcessDownloading.getId()).ifPresent(endpoint -> {
                try {
                    TransferProcess withEndpoint = transferProcessDownloading.withAssignedDataplaneEndpoint(endpoint);
                    transferProcessRepository.save(withEndpoint);
                } catch (OptimisticLockingFailureException e) {
                    log.info("Endpoint persistence skipped for {} — TP already moved forward (DP completed before save)",
                            transferProcessDownloading.getId());
                }
            });
        } catch (Exception e) {
            transferProcessRepository.save(transferProcessDownloading.withIsDownloadInProgress(false));
            return CompletableFuture.failedFuture(e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * View locally stored artifact.<br>
     * Only for TransferProcess.downloaded == true; enforce policy; read data from S3
     *
     * @param transferProcessId transfer process id
     * @return String with presigned URL for the artifact in S3
     */
    public String viewData(String transferProcessId) {
        TransferProcess transferProcess = findTransferProcessById(transferProcessId);

        if (!transferProcess.getState().equals(TransferState.COMPLETED)) {
            log.error("Transfer process is not in COMPLETED state");
            throw new DataTransferAPIException("Transfer process is not in COMPLETED state");
        }

        if (!transferProcess.isDownloaded()) {
            log.error("Transfer process data has not been downloaded yet");
            throw new DataTransferAPIException("Transfer process data has not been downloaded yet");
        }

        policyCheck(transferProcess);

        try {
            // Delegate presigned URL generation to the Data Plane so that
            // the DP can use its own S3 credentials, correct per-tenant bucket, and
            // presigning infrastructure — consistent with how provider startTransfer works.
            // The CP supplies sink.s3 metadata so the DP does not need to know about tenants.
            // TODO verify Duration does not exceed EndDateTime, if it is present
            DataFlowPrepareMessage viewPrepareMsg = applyCommonDataPlaneFields(
                            DataFlowPrepareMessage.Builder.newInstance(), transferProcess, transferProcess.getFormat())
                    .processId(transferProcess.getId())
                    .agreementId(transferProcess.getAgreementId())
                    .datasetId(transferProcess.getDatasetId())
                    .callbackAddress(dataTransferProperties.dataPlaneFeedbackAddress())
                    .metadata(buildViewPrepareMetadata(transferProcess.getTenantId(), transferProcessId))
                    .build();
            DataFlowPrepareResponse viewPrepareResponse;
            String viewStickyEndpoint;
            try {
                viewPrepareResponse = dataPlaneClient.prepare(viewPrepareMsg, transferProcess.getFormat(), null);
                // Capture sticky immediately after prepare so it can be cleared during cleanup.
                viewStickyEndpoint = dataPlaneClient.getStickyEndpoint(transferProcess.getId()).orElse(null);
            } catch (DataTransferAPIException e) {
                throw e;
            } catch (Exception prepareEx) {
                String stickyOnFailure = dataPlaneClient.getStickyEndpoint(transferProcess.getId()).orElse(null);
                cleanupPreparedDataPlaneSession(transferProcess.getId(),  transferProcess.getFormat(), null,
                        stickyOnFailure, "VIEW DP prepare failed (best-effort cleanup)");
                throw new DataTransferAPIException("HTTP-PULL DP VIEW prepare failed: " + prepareEx.getMessage());
            }
            if (viewPrepareResponse == null || viewPrepareResponse.getDataAddress() == null
                    || !viewPrepareResponse.getDataAddress().containsKey(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY)) {
                cleanupPreparedDataPlaneSession(transferProcess.getId(),  transferProcess.getFormat(), null,
                        viewStickyEndpoint, "VIEW DP terminate failed after missing presigned URL");
                throw new DataTransferAPIException("HTTP-PULL DP VIEW prepare returned no presigned URL");
            }
            String artifactURL = viewPrepareResponse.getDataAddress().get(DataPlaneConstants.DATA_ADDRESS_PRESIGNED_URL_KEY);
            // VIEW is a helper-only prepare: the DP session is not needed after the URL is obtained.
            // Terminate the PREPARED record and clear the sticky entry so neither accumulates indefinitely.
            cleanupPreparedDataPlaneSession(transferProcess.getId(),  transferProcess.getFormat(), null,
                    viewStickyEndpoint, "VIEW DP terminate failed (best-effort)");
            publisher.publishEvent(new ArtifactConsumedEvent(transferProcess.getAgreementId()));
            publisher.publishEvent(AuditEventType.TRANSFER_VIEW,
                    "Transfer process (view) generated artifact URL",
                    auditMap("transferProcess", transferProcess,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid()));
            return artifactURL;
        } catch (DataTransferAPIException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while accessing data", e);
            publisher.publishEvent(AuditEventType.TRANSFER_VIEW,
                    "Transfer process (view) generated artifact URL failed",
                    auditMap("transferProcess", transferProcess,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid(),
                            "errorMessage", e.getMessage() != null ? e.getMessage() : "Unknown error"));
            throw new DataTransferAPIException("Error while accessing data: " + e.getLocalizedMessage());
        }
    }

    /**
     * Builds an audit event map, skipping entries where the value is null.
     * Avoids {@link java.util.Map#of} throwing NullPointerException when optional
     * fields like consumerPid or providerPid are not yet populated.
     *
     * @param keyValuePairs alternating key/value pairs; null values are silently skipped
     * @return a mutable map containing only the non-null entries
     */
    private Map<String, Object> auditMap(Object... keyValuePairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
            Object value = keyValuePairs[i + 1];
            if (value != null) {
                map.put((String) keyValuePairs[i], value);
            }
        }
        return map;
    }

    /**
     * Find TransferProcess by id.<br>
     *
     * @param transferProcessId transfer process id
     * @return TransferProcess object
     */
    public TransferProcess findTransferProcessById(String transferProcessId) {
        String tenantId = TenantContextHolder.getTenantId();
        return (tenantId != null
                ? transferProcessRepository.findByIdAndTenantId(transferProcessId, tenantId)
                : transferProcessRepository.findById(transferProcessId))
                .orElseThrow(() -> {
                    publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND,
                            "Transfer process with id " + transferProcessId + " not found",
                            Map.of("transferProcessId", transferProcessId,
                                    "role", IConstants.ROLE_API,
                                    "consumerPid", IConstants.TEMPORARY_CONSUMER_PID,
                                    "providerPid", IConstants.TEMPORARY_PROVIDER_PID));
                    return new DataTransferAPIException("Transfer process with id " + transferProcessId + " not found");
                });
    }

    private void stateTransitionCheck(TransferState newState, TransferProcess transferProcess) {
        if (!transferProcess.getState().canTransitTo(newState)) {
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR,
                    "Transfer process state transition error",
                    auditMap("transferProcess", transferProcess,
                            "currentState", transferProcess.getState(),
                            "newState", newState,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid(),
                            "role", IConstants.ROLE_API));
            // Changed from API ex to TransferProcessInvalidStateException!!!
            throw new TransferProcessInvalidStateException("State transition aborted, " + transferProcess.getState().name()
                    + " state can not transition to " + newState.name(),
                    transferProcess.getConsumerPid(),
                    transferProcess.getProviderPid());
        }
    }

    private void policyCheck(TransferProcess transferProcess) {
        if (usageControlProperties.usageControlEnabled()) {
            String agreementId = transferProcess.getAgreementId();
            String response = okHttpRestClient.sendInternalRequest(ApiEndpoints.NEGOTIATION_AGREEMENTS_V1 + "/" + agreementId + "/enforce",
                    HttpMethod.POST,
                    null);
            if (StringUtils.isBlank(response)) {
                log.error("Policy check error");
                throw new DataTransferAPIException("Policy check error");
            }
            TypeReference<GenericApiResponse<String>> typeRef = new TypeReference<GenericApiResponse<String>>() {
            };
            GenericApiResponse<String> internalResponse = ToolsSerializer.deserializePlain(response, typeRef);
            if (internalResponse == null) {
                log.error("Policy check response could not be deserialized");
                throw new DataTransferAPIException("Policy check returned an invalid response");
            }
            if (!internalResponse.isSuccess()) {
                log.error("Download aborted, Policy is not valid anymore");
                throw new DataTransferAPIException("Download aborted, Policy is not valid anymore");
            }
        } else {
            log.warn("!!!!! UsageControl DISABLED - will not check if policy is present or valid !!!!!");
            publisher.publishEvent(AuditEventType.PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DISABLED,
                    "UsageControl is disabled, policy evaluation skipped",
                    auditMap("transferProcess", transferProcess,
                            "agreementId", transferProcess.getAgreementId(),
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid(),
                            "role", IConstants.ROLE_API));
        }
    }

    /**
     * Builds the DPS data address for the {@code DataFlowStartMessage} sent during
     * {@code downloadData()}. Dispatches to the HTTP-PUSH provider path when the transfer
     * process uses the {@code HttpData-PUSH} format; otherwise falls back to the standard
     * {@link #toStartMessageDataAddress} path.
     *
     * @param transferProcess the transfer process whose download is starting
     * @return schema-aligned data address for the start message
     */
    private it.eng.dataplane.api.message.DataAddress buildStartMessageDataAddress(TransferProcess transferProcess) {
        if (DataTransferFormat.HTTP_PUSH.format().equals(transferProcess.getFormat())) {
            return buildHttpPushProviderStartDataAddress(transferProcess);
        }
        return toStartMessageDataAddress(transferProcess.getDataAddress(),
                transferProcess.getFormat(),
                transferProcess.getTenantId(),
                transferProcess.getId());
    }

    /**
     * Builds the DPS data address for an HTTP-PUSH provider start message.
     *
     * <p>S3 coordinates for both source and sink are now carried exclusively in
     * {@code metadata.source.s3} / {@code metadata.sink.s3} (built by
     * {@link #buildStartMetadata}). The {@code dataAddress} carries only the transport
     * {@code endpointType}; no flat {@code source.*} or {@code sink.*} properties are added.</p>
     *
     * @param transferProcess the provider-side HTTP-PUSH transfer process
     * @return schema-aligned data address containing only {@code endpointType}
     */
    private it.eng.dataplane.api.message.DataAddress buildHttpPushProviderStartDataAddress(
            TransferProcess transferProcess) {
        String endpointType = defaultStartMessageEndpointType(transferProcess.getFormat());
        return it.eng.dataplane.api.message.DataAddress.Builder.newInstance()
                .endpointType(endpointType)
                .build();
    }

    /**
     * Converts a list of {@link EndpointProperty} objects from the consumer's flat S3 keys
     * ({@code bucketName}, {@code objectKey}, {@code accessKey}, {@code secretKey},
     * {@code region}, {@code endpointOverride}) to their {@code sink.*} counterparts.
     *
     * @param consumerProperties the flat endpoint properties from the consumer TP dataAddress; may be {@code null}
     * @return list of {@code sink.*} endpoint properties; empty when input is {@code null} or empty
     */
    private List<it.eng.dataplane.api.message.EndpointProperty> translateConsumerFlatToSinkProperties(
            List<EndpointProperty> consumerProperties) {
        if (consumerProperties == null || consumerProperties.isEmpty()) {
            return List.of();
        }
        Map<String, String> flatToSink = Map.of(
                S3Utils.BUCKET_NAME, DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME,
                S3Utils.OBJECT_KEY, DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY,
                S3Utils.ACCESS_KEY, DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY,
                S3Utils.SECRET_KEY, DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY,
                S3Utils.REGION, DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION,
                S3Utils.ENDPOINT_OVERRIDE, DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE
        );
        List<it.eng.dataplane.api.message.EndpointProperty> result = new ArrayList<>();
        for (EndpointProperty property : consumerProperties) {
            if (property == null || StringUtils.isBlank(property.getName())) {
                continue;
            }
            String sinkKey = flatToSink.get(property.getName());
            if (sinkKey != null && property.getValue() != null) {
                result.add(it.eng.dataplane.api.message.EndpointProperty.Builder.newInstance()
                        .name(sinkKey)
                        .value(property.getValue())
                        .build());
            }
        }
        return result;
    }

    /**
     * Converts a DSP {@link DataAddress} into a schema-aligned DPS data address.
     *
     * <p>S3 sink coordinates are now carried exclusively in {@code metadata.sink.s3}
     * (built by {@link #buildStartMetadata}). Only the transport fields originally present
     * in the DSP {@code dataAddress} (e.g. {@code endpoint}, {@code endpointType},
     * {@code authorization}) are forwarded here.</p>
     *
     * @param dataAddress the data address to convert; may be {@code null}
     * @param transferType the transfer type associated with the start message
     * @param tenantId the tenant that owns the sink bucket (unused; kept for signature compatibility)
     * @param sinkObjectKey the sink object key chosen by the control plane (unused; now in metadata)
     * @return schema-aligned data address, or {@code null} when input is {@code null}
     */
    private it.eng.dataplane.api.message.DataAddress toStartMessageDataAddress(DataAddress dataAddress,
                                                                               String transferType,
                                                                               String tenantId,
                                                                               String sinkObjectKey) {
        List<it.eng.dataplane.api.message.EndpointProperty> endpointProperties = new ArrayList<>();
        if (dataAddress != null && dataAddress.getEndpointProperties() != null) {
            endpointProperties.addAll(dataAddress.getEndpointProperties().stream()
                    .map(property -> it.eng.dataplane.api.message.EndpointProperty.Builder.newInstance()
                            .name(property.getName())
                            .value(property.getValue())
                            .build())
                    .toList());
        }
        String endpointType = StringUtils.defaultIfBlank(dataAddress == null ? null : dataAddress.getEndpointType(),
                defaultStartMessageEndpointType(transferType));
        return it.eng.dataplane.api.message.DataAddress.Builder.newInstance()
                .endpoint(dataAddress == null ? null : dataAddress.getEndpoint())
                .endpointType(endpointType)
                .endpointProperties(endpointProperties)
                .build();
    }

    private Map<String, Object> toStructuredSectionMetadata(DataAddress dataAddress) {
        if (dataAddress == null || dataAddress.getEndpointProperties() == null) {
            return Map.of();
        }
        Map<String, Object> section = new LinkedHashMap<>();
        Map<String, Object> s3Section = new LinkedHashMap<>();
        for (EndpointProperty property : dataAddress.getEndpointProperties()) {
            if (property == null || StringUtils.isBlank(property.getName()) || property.getValue() == null) {
                continue;
            }
            String s3MetadataKey = toS3MetadataKey(property.getName());
            if (s3MetadataKey != null) {
                s3Section.put(s3MetadataKey, property.getValue());
            } else if (!isS3MetadataField(property.getName())) {
                section.put(property.getName(), property.getValue());
            }
        }
        if (!s3Section.isEmpty()) {
            section.put(DataPlaneConstants.METADATA_SECTION_S3, Map.copyOf(s3Section));
        }
        return section.isEmpty() ? Map.of() : Map.copyOf(section);
    }

    private List<it.eng.dataplane.api.message.EndpointProperty> buildStartSinkEndpointProperties(String tenantId,
                                                                                                  String objectKey) {
        Map<String, String> sinkProperties = buildScopedSinkS3Properties(tenantId, objectKey);
        return sinkProperties.entrySet().stream()
                .map(entry -> it.eng.dataplane.api.message.EndpointProperty.Builder.newInstance()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .toList();
    }

    /**
     * Builds a list of {@code source.*} endpoint properties for the provider's own S3 bucket.
     * Mirrors {@link #buildStartSinkEndpointProperties} but uses {@link DataPlaneConstants#DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME}
     * and related SOURCE constants.
     *
     * @param tenantId  the tenant that owns the source bucket
     * @param objectKey the source object key (dataset ID for HTTP-PUSH provider)
     * @return list of {@code source.*} endpoint properties
     */
    private List<it.eng.dataplane.api.message.EndpointProperty> buildStartSourceEndpointProperties(String tenantId,
                                                                                                   String objectKey) {
        Map<String, String> s3PropertiesMap = buildControlPlaneS3Properties(tenantId, objectKey);
        Map<String, String> sourceProperties = new LinkedHashMap<>();
        sourceProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_BUCKET_NAME,
                s3PropertiesMap.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        sourceProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_OBJECT_KEY,
                s3PropertiesMap.get(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
        sourceProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_REGION,
                s3PropertiesMap.get(DataPlaneConstants.METADATA_S3_REGION));
        sourceProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_ACCESS_KEY,
                s3PropertiesMap.get(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        sourceProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_SECRET_KEY,
                s3PropertiesMap.get(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        String endpointOverride = s3PropertiesMap.get(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE);
        if (endpointOverride != null) {
            sourceProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SOURCE_ENDPOINT_OVERRIDE, endpointOverride);
        }
        return sourceProperties.entrySet().stream()
                .map(entry -> it.eng.dataplane.api.message.EndpointProperty.Builder.newInstance()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .toList();
    }

    private Map<String, Object> buildControlPlaneS3Metadata(String tenantId, String objectKey) {
        Map<String, String> s3PropertiesMap = buildControlPlaneS3Properties(tenantId, objectKey);
        Map<String, Object> s3Metadata = new LinkedHashMap<>();
        s3PropertiesMap.forEach(s3Metadata::put);
        return Map.copyOf(s3Metadata);
    }

    private Map<String, Object> buildStartMetadata(TransferProcess transferProcess) {
        if (DataTransferFormat.HTTP_PUSH.format().equals(transferProcess.getFormat())) {
            return buildCanonicalMetadata(
                    buildCanonicalSection(Map.of(),
                            buildControlPlaneS3Metadata(transferProcess.getTenantId(), transferProcess.getDatasetId()),
                            null),
                    buildCanonicalSection(toStructuredSectionMetadata(transferProcess.getDataAddress()), Map.of(), null));
        }
        return buildCanonicalMetadata(
                buildCanonicalSection(toStructuredSectionMetadata(transferProcess.getDataAddress()), Map.of(), null),
                buildCanonicalSection(Map.of(),
                        buildControlPlaneS3Metadata(transferProcess.getTenantId(), transferProcess.getId()),
                        null));
    }

    private Map<String, Object> buildCanonicalMetadata(Map<String, Object> sourceSection,
                                                       Map<String, Object> sinkSection) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (!sourceSection.isEmpty()) {
            metadata.put(DataPlaneConstants.METADATA_SECTION_SOURCE, Map.copyOf(sourceSection));
        }
        if (!sinkSection.isEmpty()) {
            metadata.put(DataPlaneConstants.METADATA_SECTION_SINK, Map.copyOf(sinkSection));
        }
        return metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
    }

    private Map<String, Object> buildCanonicalSection(Map<String, Object> sectionMetadata,
                                                      Map<String, Object> s3Metadata,
                                                      String mode) {
        Map<String, Object> section = new LinkedHashMap<>(sectionMetadata);
        if (StringUtils.isNotBlank(mode)) {
            section.put(DataPlaneConstants.METADATA_FIELD_MODE, mode);
        }
        if (!s3Metadata.isEmpty()) {
            section.put(DataPlaneConstants.METADATA_SECTION_S3, Map.copyOf(s3Metadata));
        }
        return section.isEmpty() ? Map.of() : Map.copyOf(section);
    }

    private Map<String, Object> buildBootstrapManagementS3Metadata(String tenantId, String objectKey) {
        String bucketName = tenantBucketResolver.resolveBucketName(tenantId);
        Map<String, Object> s3Section = new LinkedHashMap<>();
        s3Section.put(DataPlaneConstants.METADATA_S3_BUCKET_NAME, bucketName);
        s3Section.put(DataPlaneConstants.METADATA_S3_OBJECT_KEY, objectKey);
        String region = s3Properties.getRegion();
        if (StringUtils.isNotBlank(region)) {
            s3Section.put(DataPlaneConstants.METADATA_S3_REGION, region);
        }
        String endpoint = s3Properties.getEndpoint();
        if (StringUtils.isNotBlank(endpoint)) {
            s3Section.put(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, endpoint);
        }
        // Temporary Minio fallback: pass bootstrap admin credentials to the DP until
        // BucketCredentialsEntity-backed delegated policies are working end-to-end.
        s3Section.put(DataPlaneConstants.METADATA_S3_ACCESS_KEY,
                requireControlPlaneS3Configuration(S3Utils.ACCESS_KEY, s3Properties.getAccessKey()));
        s3Section.put(DataPlaneConstants.METADATA_S3_SECRET_KEY,
                requireControlPlaneS3Configuration(S3Utils.SECRET_KEY, s3Properties.getSecretKey()));
        return Map.copyOf(s3Section);
    }

    private Map<String, Object> buildViewS3Metadata(String tenantId, String objectKey) {
        String bucketName = tenantBucketResolver.resolveBucketName(tenantId);
        BucketCredentialsEntity bucketCredentials = bucketCredentialsService.getBucketCredentials(bucketName);
        String region = requireControlPlaneS3Configuration(S3Utils.REGION, s3Properties.getRegion());
        String accessKey = requireControlPlaneS3Credential(bucketName, S3Utils.ACCESS_KEY,
                bucketCredentials == null ? null : bucketCredentials.getAccessKey());
        String secretKey = requireControlPlaneS3Credential(bucketName, S3Utils.SECRET_KEY,
                bucketCredentials == null ? null : bucketCredentials.getSecretKey());

        Map<String, Object> s3Section = new LinkedHashMap<>();
        s3Section.put(DataPlaneConstants.METADATA_S3_BUCKET_NAME, bucketName);
        s3Section.put(DataPlaneConstants.METADATA_S3_OBJECT_KEY, objectKey);
        s3Section.put(DataPlaneConstants.METADATA_S3_ACCESS_KEY, accessKey);
        s3Section.put(DataPlaneConstants.METADATA_S3_SECRET_KEY, secretKey);
        if (StringUtils.isNotBlank(region)) {
            s3Section.put(DataPlaneConstants.METADATA_S3_REGION, region);
        }
        if (StringUtils.isNotBlank(s3Properties.getExternalPresignedEndpoint())) {
            s3Section.put(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, s3Properties.getExternalPresignedEndpoint());
        }
        return Map.copyOf(s3Section);
    }

    /**
     * Builds the DPS prepare metadata for an HTTP-PULL VIEW request.
     *
     * <p>The sink section carries the consumer's own S3 bucket and object coordinates so
     * the Data Plane can generate a presigned GET URL for the stored artifact. The DP must
     * use tenant-scoped bucket credentials for presigning so the generated URL reflects the
     * actual runtime bucket identity rather than bootstrap admin credentials.</p>
     *
     * @param tenantId  the tenant that owns the data (used to resolve the per-tenant bucket)
     * @param objectKey the S3 object key (transfer process ID used at download time)
     * @return metadata map with {@code sink.mode = METADATA_MODE_VIEW} and {@code sink.s3} bucket/object info
     */
    private Map<String, Object> buildViewPrepareMetadata(String tenantId, String objectKey) {
        return buildCanonicalMetadata(
                Map.of(),
                buildCanonicalSection(Map.of(), buildViewS3Metadata(tenantId, objectKey), DataPlaneConstants.METADATA_MODE_VIEW));
    }

    private Map<String, String> buildScopedSinkS3Properties(String tenantId, String objectKey) {
        Map<String, String> s3PropertiesMap = buildControlPlaneS3Properties(tenantId, objectKey);
        Map<String, String> sinkProperties = new LinkedHashMap<>();
        sinkProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_BUCKET_NAME,
                s3PropertiesMap.get(DataPlaneConstants.METADATA_S3_BUCKET_NAME));
        sinkProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_OBJECT_KEY,
                s3PropertiesMap.get(DataPlaneConstants.METADATA_S3_OBJECT_KEY));
        sinkProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_REGION,
                s3PropertiesMap.get(DataPlaneConstants.METADATA_S3_REGION));
        sinkProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ACCESS_KEY,
                s3PropertiesMap.get(DataPlaneConstants.METADATA_S3_ACCESS_KEY));
        sinkProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_SECRET_KEY,
                s3PropertiesMap.get(DataPlaneConstants.METADATA_S3_SECRET_KEY));
        String endpointOverride = s3PropertiesMap.get(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE);
        if (endpointOverride != null) {
            sinkProperties.put(DataPlaneConstants.DATA_ADDRESS_PROPERTY_SINK_ENDPOINT_OVERRIDE, endpointOverride);
        }
        return Map.copyOf(sinkProperties);
    }

    private Map<String, String> buildControlPlaneS3Properties(String tenantId, String objectKey) {
        String bucketName = tenantBucketResolver.resolveBucketName(tenantId);
        BucketCredentialsEntity bucketCredentials = bucketCredentialsService.getBucketCredentials(bucketName);
        String region = requireControlPlaneS3Configuration(S3Utils.REGION, s3Properties.getRegion());
        String accessKey = requireControlPlaneS3Credential(bucketName, S3Utils.ACCESS_KEY,
                bucketCredentials == null ? null : bucketCredentials.getAccessKey());
        String secretKey = requireControlPlaneS3Credential(bucketName, S3Utils.SECRET_KEY,
                bucketCredentials == null ? null : bucketCredentials.getSecretKey());

        Map<String, String> s3Metadata = new LinkedHashMap<>();
        s3Metadata.put(DataPlaneConstants.METADATA_S3_BUCKET_NAME, bucketName);
        s3Metadata.put(DataPlaneConstants.METADATA_S3_OBJECT_KEY, objectKey);
        s3Metadata.put(DataPlaneConstants.METADATA_S3_REGION, region);
        s3Metadata.put(DataPlaneConstants.METADATA_S3_ACCESS_KEY, accessKey);
        s3Metadata.put(DataPlaneConstants.METADATA_S3_SECRET_KEY, secretKey);
        if (StringUtils.isNotBlank(s3Properties.getEndpoint())) {
            s3Metadata.put(DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE, s3Properties.getEndpoint());
        }
        return Map.copyOf(s3Metadata);
    }

    private String requireControlPlaneS3Configuration(String propertyName, String propertyValue) {
        if (StringUtils.isBlank(propertyValue)) {
            throw new DataTransferAPIException("Missing required control plane S3 configuration: " + propertyName);
        }
        return propertyValue;
    }

    private String requireControlPlaneS3Credential(String bucketName, String credentialName, String credentialValue) {
        if (StringUtils.isBlank(credentialValue)) {
            throw new DataTransferAPIException("Missing required control plane S3 credentials for bucket "
                    + bucketName + ": " + credentialName);
        }
        return credentialValue;
    }

    private boolean isS3MetadataField(String fieldName) {
        return DataPlaneConstants.METADATA_S3_BUCKET_NAME.equals(fieldName)
                || DataPlaneConstants.METADATA_S3_OBJECT_KEY.equals(fieldName)
                || DataPlaneConstants.METADATA_S3_REGION.equals(fieldName)
                || DataPlaneConstants.METADATA_S3_ACCESS_KEY.equals(fieldName)
                || DataPlaneConstants.METADATA_S3_SECRET_KEY.equals(fieldName)
                || DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE.equals(fieldName);
    }

    private String toS3MetadataKey(String fieldName) {
        return switch (fieldName) {
            case S3Utils.BUCKET_NAME -> DataPlaneConstants.METADATA_S3_BUCKET_NAME;
            case S3Utils.OBJECT_KEY -> DataPlaneConstants.METADATA_S3_OBJECT_KEY;
            case S3Utils.REGION -> DataPlaneConstants.METADATA_S3_REGION;
            case S3Utils.ACCESS_KEY -> DataPlaneConstants.METADATA_S3_ACCESS_KEY;
            case S3Utils.SECRET_KEY -> DataPlaneConstants.METADATA_S3_SECRET_KEY;
            case S3Utils.ENDPOINT_OVERRIDE -> DataPlaneConstants.METADATA_S3_ENDPOINT_OVERRIDE;
            default -> null;
        };
    }

    private String defaultStartMessageEndpointType(String transferType) {
        if (TransportProfile.STREAM_GRPC.equals(transferType)) {
            return "grpc";
        }
        if (TransportProfile.STREAM_KAFKA.equals(transferType)) {
            return "kafka";
        }
        if (DataTransferFormat.HTTP_PUSH.format().equals(transferType)) {
            return "s3";
        }
        return "generic";
    }
}
