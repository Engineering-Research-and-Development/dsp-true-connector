package it.eng.datatransfer.service.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowPrepareResponse;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.datatransfer.client.DataPlaneClient;
import it.eng.datatransfer.exceptions.DataPlaneClientException;
import it.eng.datatransfer.exceptions.DataTransferAPIException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.rest.protocol.DataTransferCallback;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.serializer.ToolsSerializer;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.TenantBucketResolver;
import it.eng.tools.service.TenantContextHolder;
import it.eng.tools.usagecontrol.UsageControlProperties;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final S3ClientService s3ClientService;
    private final TenantBucketResolver tenantBucketResolver;

    /**
     * Creates a new {@code DataTransferAPIService}.
     *
     * @param transferProcessRepository repository for transfer processes
     * @param okHttpRestClient          HTTP client for protocol messages
     * @param credentialUtils           connector credential provider
     * @param dataTransferProperties    configuration properties
     * @param usageControlProperties    usage control configuration
     * @param publisher                 audit event publisher
     * @param artifactTransferService   artifact lookup service
     * @param dataPlaneClient           DPS client for forwarding data-flow messages
     * @param s3ClientService           S3 client for presigned URL generation
     * @param tenantBucketResolver      resolves the effective bucket name for the current tenant
     */
    public DataTransferAPIService(TransferProcessRepository transferProcessRepository,
                                  OkHttpRestClient okHttpRestClient,
                                  CredentialUtils credentialUtils,
                                  DataTransferProperties dataTransferProperties,
                                  UsageControlProperties usageControlProperties,
                                  AuditEventPublisher publisher,
                                  ArtifactTransferService artifactTransferService,
                                  DataPlaneClient dataPlaneClient,
                                  S3ClientService s3ClientService,
                                  TenantBucketResolver tenantBucketResolver) {
        super();
        this.transferProcessRepository = transferProcessRepository;
        this.okHttpRestClient = okHttpRestClient;
        this.credentialUtils = credentialUtils;
        this.dataTransferProperties = dataTransferProperties;
        this.usageControlProperties = usageControlProperties;
        this.publisher = publisher;
        this.artifactTransferService = artifactTransferService;
        this.dataPlaneClient = dataPlaneClient;
        this.s3ClientService = s3ClientService;
        this.tenantBucketResolver = tenantBucketResolver;
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
        boolean isHttpPush = "HttpData-PUSH".equals(dataTransferRequest.getFormat());
        DataFlowPrepareResponse prepareResponse = null;
        if (isHttpPush) {
            DataFlowPrepareMessage prepareMessage = DataFlowPrepareMessage.Builder.newInstance()
                    .processId(transferProcessInitialized.getId())
                    .callbackAddress(dataTransferProperties.providerCallbackAddress())
                    .agreementId(transferProcessInitialized.getAgreementId())
                    .datasetId(transferProcessInitialized.getDatasetId())
                    .build();
            try {
                prepareResponse = dataPlaneClient.prepare(prepareMessage, dataTransferRequest.getFormat());
            } catch (DataPlaneClientException e) {
                log.error("Data Plane prepare failed for process {}: {}", transferProcessInitialized.getId(), e.getMessage());
                TransferProcess terminated = transferProcessInitialized.copyWithNewTransferState(TransferState.TERMINATED);
                transferProcessRepository.save(terminated);
                return TransferSerializer.serializePlainJsonNode(terminated);
            }
            Map<String, String> dpDataAddress = prepareResponse.getDataAddress();

            List<EndpointProperty> endpointProperties = dpDataAddress.entrySet().stream()
                    .map(e -> EndpointProperty.Builder.newInstance()
                            .name(e.getKey())
                            .value(e.getValue())
                            .build())
                    .toList();

            dataAddressForMessage = DataAddress.Builder.newInstance()
                    .endpointProperties(endpointProperties)
                    .build();
        }

        TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
                .agreementId(transferProcessInitialized.getAgreementId())
                .callbackAddress(dataTransferProperties.consumerCallbackAddress())
                .consumerPid(transferProcessInitialized.getConsumerPid())
                .format(dataTransferRequest.getFormat())
                .dataAddress(dataAddressForMessage)
                .build();

        boolean succeeded = false;
        try {
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
                succeeded = true;
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
                    throw new DataTransferAPIException(transferError, "Error making request");
                } catch (jakarta.validation.ValidationException ve) {
                    log.warn("Provider error response is not a DSP TransferError: {}", response.getData());
                    throw new DataTransferAPIException("Transfer request failed: " + response.getMessage());
                }
            } catch (JsonProcessingException ex) {
                throw new DataTransferAPIException("Error occurred");
            }
        }
        return TransferSerializer.serializePlainJsonNode(transferProcessForDB);
        } finally {
            if (isHttpPush && !succeeded && prepareResponse != null) {
                // Prepare created temp credentials in the push DP — clean them up via terminate
                try {
                    dataPlaneClient.terminate(transferProcessInitialized.getId(), dataTransferRequest.getFormat());
                } catch (Exception e) {
                    log.warn("Could not clean up DP resources after requestTransfer failure for process {}: {}",
                            transferProcessInitialized.getId(), e.getMessage());
                }
            }
        }
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

        if (StringUtils.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole()) && TransferState.REQUESTED.equals(transferProcess.getState())) {
            throw new DataTransferAPIException("State transition aborted, consumer can not transit from " + transferProcess.getState().name()
                    + " to " + TransferState.STARTED.name());
        }

        stateTransitionCheck(TransferState.STARTED, transferProcess);

        log.info("Sending TransferStartMessage to {}", transferProcess.getCallbackAddress());
        String address = null;
        DataAddress dataAddress = null;

        if (StringUtils.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
            address = DataTransferCallback.getProviderDataTransferStart(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
        }
        if (StringUtils.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
            address = DataTransferCallback.getConsumerDataTransferStart(transferProcess.getCallbackAddress(), transferProcess.getConsumerPid());
            if ("HttpData-PULL".equals(transferProcess.getFormat())) {
                Artifact artifact = artifactTransferService.findArtifact(transferProcess);
                String artifactURL = switch (artifact.getArtifactType()) {
                    case FILE -> {
                        // Generate presigned download URL directly using the connector's own S3 client.
                        // No pull Data Plane is registered at the provider side; the pull DP is a
                        // consumer-side component that downloads from this URL.
                        try {
                            String bucketName = tenantBucketResolver.resolveBucketName(transferProcess.getTenantId());
                            yield s3ClientService.generateGetPresignedUrl(bucketName, transferProcess.getDatasetId(), Duration.ofDays(7L));
                        } catch (Exception e) {
                            throw new DataTransferAPIException("The requested artifact is currently not available. Please try again later.");
                        }
                    }
                    case EXTERNAL -> {
                        String transactionId = Base64.encodeBase64URLSafeString((transferProcess.getConsumerPid() + "|" + transferProcess.getProviderPid()).getBytes(StandardCharsets.UTF_8));
                        yield DataTransferCallback.getValidCallback(dataTransferProperties.providerCallbackAddress()) + "/artifacts/" + transactionId;
                    }
                };

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

        if (address == null) {
            throw new DataTransferAPIException("Cannot resolve callback address for unknown role: " + transferProcess.getRole());
        }

        TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
                .consumerPid(transferProcess.getConsumerPid())
                .providerPid(transferProcess.getProviderPid())
                .dataAddress(transferProcess.getDataAddress() == null ? dataAddress : transferProcess.getDataAddress())
                .build();

        GenericApiResponse<String> response = okHttpRestClient
                .sendRequestProtocol(address,
                        TransferSerializer.serializeProtocolJsonNode(transferStartMessage),
                        credentialUtils.getConnectorCredentials());
        log.info("Response received {}", response);
        if (response.isSuccess()) {
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
                    .created(transferProcess.getCreated())
                    .createdBy(transferProcess.getCreatedBy())
                    .modified(transferProcess.getModified())
                    .lastModifiedBy(transferProcess.getLastModifiedBy())
                    .version(transferProcess.getVersion())
                    .build();
            transferProcessRepository.save(transferProcessStarted);
            log.info("Transfer process {} saved", transferProcessStarted.getId());
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_STARTED,
                    "Transfer process started successfully",
                    auditMap("transferProcess", transferProcessStarted,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcessStarted.getConsumerPid(),
                            "providerPid", transferProcessStarted.getProviderPid()));
            return TransferSerializer.serializePlainJsonNode(transferProcessStarted);
        } else {
            log.error("Error response received!");
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
        if (StringUtils.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
            address = DataTransferCallback.getProviderDataTransferCompletion(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
        }
        if (StringUtils.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
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
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_COMPLETED,
                    "Transfer process completed successfully",
                    auditMap("transferProcess", transferProcessCompleted,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcessCompleted.getConsumerPid(),
                            "providerPid", transferProcessCompleted.getProviderPid()));
            // Temp user cleanup (HTTP-PUSH) is now handled by the push Data Plane
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

        if (StringUtils.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
            address = DataTransferCallback.getProviderDataTransferSuspension(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
        }
        if (StringUtils.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
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

        if (StringUtils.equals(IConstants.ROLE_CONSUMER, transferProcess.getRole())) {
            address = DataTransferCallback.getProviderDataTransferTermination(transferProcess.getCallbackAddress(), transferProcess.getProviderPid());
        }
        if (StringUtils.equals(IConstants.ROLE_PROVIDER, transferProcess.getRole())) {
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
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_TERMINATED,
                    "Transfer process terminated successfully",
                    auditMap("transferProcess", transferProcess,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid()));
            // Temp user cleanup (HTTP-PUSH) is now handled by the push Data Plane
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
        // The @Version field provides optimistic locking: a concurrent request that also
        // passed the isDownloadInProgress check above will fail here with OptimisticLockingFailureException.
        TransferProcess transferProcessDownloading;
        try {
            transferProcessDownloading = transferProcessRepository.save(transferProcess.withIsDownloadInProgress(true));
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
            DataFlowStartMessage startMessage = DataFlowStartMessage.Builder.newInstance()
                    .processId(transferProcessDownloading.getId())
                    .transferType(transferProcessDownloading.getFormat())
                    .callbackAddress(callbackAddress)
                    .dataAddress(toDataAddressMap(transferProcessDownloading.getDataAddress()))
                    .agreementId(transferProcessDownloading.getAgreementId())
                    .datasetId(transferProcessDownloading.getDatasetId())
                    .build();
            dataPlaneClient.start(startMessage);
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
            // Generate presigned URL directly using the connector's own S3 client.
            // The artifact is stored in the consumer's S3 bucket with the transferProcessId as the object key.
            // TODO verify Duration does not exceed EndDateTime, if it is present
            String bucketName = tenantBucketResolver.resolveBucketName(transferProcess.getTenantId());
            String artifactURL = s3ClientService.generateGetPresignedUrl(bucketName, transferProcessId, Duration.ofDays(7L));
            publisher.publishEvent(new ArtifactConsumedEvent(transferProcess.getAgreementId()));
            publisher.publishEvent(AuditEventType.TRANSFER_VIEW,
                    "Transfer process (view) generated artifact URL",
                    auditMap("transferProcess", transferProcess,
                            "role", IConstants.ROLE_API,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid()));
            return artifactURL;
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
     * Attempts to forward a {@link DataFlowStartMessage} to the registered Data Plane for the given
     * transfer process. If no Data Plane is registered for the transfer type the call is skipped
     * silently (backward-compatible fallback). If the Data Plane call fails with a
     * {@link DataPlaneClientException}, {@link #terminateTransfer(String)} is called so the consumer
     * is notified and the local state is set to TERMINATED. If notifying the counter-party also
     * fails, the TERMINATED state is persisted locally as a best-effort fallback.
     * If the transfer type is not set the call is skipped silently.
     *
     * @param transferProcess the provider-side process that just moved to STARTED
     */
    private void sendDataFlowStartToDataPlane(TransferProcess transferProcess) {
        String transferType = transferProcess.getFormat();
        if (StringUtils.isBlank(transferType)) {
            log.debug("Transfer type not set for process {}, skipping Data Plane routing", transferProcess.getId());
            return;
        }
        // Use the global feedback address (no tenant path) so the DP can POST back to
        // /api/v1/dataflows/complete on the admin chain.
        String callbackAddress = dataTransferProperties.dataPlaneFeedbackAddress();
        DataFlowStartMessage startMessage = DataFlowStartMessage.Builder.newInstance()
                .processId(transferProcess.getId())
                .transferType(transferType)
                .callbackAddress(callbackAddress)
                .dataAddress(toDataAddressMap(transferProcess.getDataAddress()))
                .agreementId(transferProcess.getAgreementId())
                .datasetId(transferProcess.getDatasetId())
                .build();
        try {
            dataPlaneClient.start(startMessage);
            log.info("DataFlowStartMessage forwarded to Data Plane for process {}", transferProcess.getId());
        } catch (IllegalStateException e) {
            log.info("No Data Plane registered for transfer type '{}', skipping DPS routing for process {}",
                    transferType, transferProcess.getId());
        } catch (DataPlaneClientException e) {
            log.error("Data Plane communication failed for process {}: {}. Terminating transfer.",
                    transferProcess.getId(), e.getMessage());
            try {
                terminateTransfer(transferProcess.getId());
            } catch (Exception terminateEx) {
                log.error("Failed to send termination to counter-party: {}", terminateEx.getMessage());
                TransferProcess terminated = transferProcess.copyWithNewTransferState(TransferState.TERMINATED);
                transferProcessRepository.save(terminated);
            }
        }
    }

    /**
     * Converts a {@link DataAddress} model object to a flat string map suitable for
     * {@link DataFlowStartMessage} and {@link DataFlowPrepareMessage}.
     *
     * @param dataAddress the data address to convert; may be {@code null}
     * @return a mutable map of address properties, or {@code null} when input is {@code null}
     */
    private Map<String, String> toDataAddressMap(DataAddress dataAddress) {
        if (dataAddress == null) {
            return null;
        }
        Map<String, String> map = new HashMap<>();
        if (dataAddress.getEndpoint() != null) {
            map.put("endpoint", dataAddress.getEndpoint());
        }
        if (dataAddress.getEndpointType() != null) {
            map.put("endpointType", dataAddress.getEndpointType());
        }
        if (dataAddress.getEndpointProperties() != null) {
            dataAddress.getEndpointProperties()
                    .forEach(p -> map.put(p.getName(), p.getValue()));
        }
        return map;
    }
}
