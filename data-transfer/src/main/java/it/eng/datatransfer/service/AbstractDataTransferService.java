package it.eng.datatransfer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import it.eng.datatransfer.event.TransferProcessChangeEvent;
import it.eng.datatransfer.exceptions.TransferProcessInternalException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidFormatException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class AbstractDataTransferService implements TransferProcessStrategy {

    private final TransferProcessRepository transferProcessRepository;
    private final AuditEventPublisher publisher;
    private final OkHttpRestClient okHttpRestClient;

    // Consider this for removal
    private final TransferRequestMessageRepository transferRequestMessageRepository;

    protected AbstractDataTransferService(TransferProcessRepository transferProcessRepository,
                                          AuditEventPublisher publisher,
                                          OkHttpRestClient okHttpRestClient,
                                          TransferRequestMessageRepository transferRequestMessageRepository) {
        this.transferProcessRepository = transferProcessRepository;
        this.publisher = publisher;
        this.okHttpRestClient = okHttpRestClient;
        this.transferRequestMessageRepository = transferRequestMessageRepository;
    }

    /**
     * Find transferProcess for given providerPid.
     *
     * @param providerPid providerPid to search by
     * @return TransferProcess
     */
    @Override
    public TransferProcess findTransferProcessByProviderPid(String providerPid) {
        TransferProcess tp = transferProcessRepository.findByProviderPid(providerPid)
                .orElseThrow(() -> new TransferProcessNotFoundException("No transfer process found for providerPid: " + providerPid));
        log.info("Found transfer process: consumerPid {}, providerPid{} , state {}", tp.getConsumerPid(), tp.getProviderPid(), tp.getState());
        return tp;
    }

    /**
     * Find transferProcess for given consumerPid.
     *
     * @param consumerPid consumerPid to search by
     * @return TransferProcess
     */
    @Override
    public TransferProcess findTransferProcessByConsumerPid(String consumerPid) {
        return transferProcessRepository.findByConsumerPid(consumerPid)
                .orElseThrow(() -> new TransferProcessNotFoundException("No transfer process found for consumerPid: " + consumerPid));
    }

    /**
     * Find transferProcess for given providerPid.
     *
     * @param consumerPid consumerPid to search by
     * @param providerPid providerPid to search by
     * @return TransferProcess
     */
    public TransferProcess findByConsumerPidAndProviderPid(String consumerPid, String providerPid) {
        return transferProcessRepository.findByConsumerPidAndProviderPid(consumerPid, providerPid)
                .orElseThrow(() -> new TransferProcessNotFoundException("No transfer process found"));
    }

    /**
     * Find transferProcess for given agreementId.
     *
     * @param agreementId agreementId to search by
     * @return TransferProcess
     */
    public TransferProcess findByAgreementId(String agreementId) {
        return transferProcessRepository.findByAgreementId(agreementId)
                .orElseThrow(() -> new TransferProcessNotFoundException("No transfer process found for agreementId: " + agreementId));
    }

    /**
     * Save or update TransferProcess.
     *
     * @param transferProcess TransferProcess to save
     * @return saved TransferProcess
     */
    public TransferProcess saveTransferProcess(TransferProcess transferProcess) {
        return transferProcessRepository.save(transferProcess);
    }

    /**
     * If TransferProcess for given consumerPid and providerPid exists and state is STARTED.<br>
     * Note: those 2 Pid's are not to be mixed with Contract Negotiation ones. They are unique
     *
     * @param consumerPid consumerPid to search by
     * @param providerPid providerPid to search by
     * @return true if there is transferProcess with state STARTED for consumerPid and providerPid
     */
    public boolean isDataTransferStarted(String consumerPid, String providerPid) {
        return transferProcessRepository.findByConsumerPidAndProviderPid(consumerPid, providerPid)
                .map(tp -> TransferState.STARTED.equals(tp.getState()))
                .orElse(false);
    }

    /**
     * Initiate data transfer.
     *
     * @param transferRequestMessage message
     * @return TransferProcess with status REQUESTED
     */
    public TransferProcess initiateDataTransfer(TransferRequestMessage transferRequestMessage) {
        TransferProcess transferProcessInitialized = transferProcessRepository.findByAgreementId(transferRequestMessage.getAgreementId())
                .orElseThrow(() ->
                {
                    String errorMessage = "No agreement with id " + transferRequestMessage.getAgreementId() +
                            " exists or Contract Negotiation not finalized";
                    publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND,
                            "Transfer process not found for agreementId " + transferRequestMessage.getAgreementId(),
                            Map.of("role", IConstants.ROLE_PROTOCOL,
                                    "transferRequestMessage", transferRequestMessage,
                                    "errorMessage", errorMessage));
                    return new TransferProcessNotFoundException(errorMessage);
                });
        log.info("Found TransferProcess in INITIALIZED state for agreementId {}", transferRequestMessage.getAgreementId());
        stateTransitionCheck(transferProcessInitialized, TransferState.REQUESTED);

        // check if TransferRequestMessage.format is supported by dataset.[distribution]
        checkSupportedFormats(transferProcessInitialized, transferRequestMessage.getFormat());

        transferRequestMessageRepository.save(transferRequestMessage);

        TransferProcess transferProcessRequested = TransferProcess.Builder.newInstance()
                .id(transferProcessInitialized.getId())
                .agreementId(transferRequestMessage.getAgreementId())
                .callbackAddress(transferRequestMessage.getCallbackAddress())
                .consumerPid(transferRequestMessage.getConsumerPid())
                .providerPid(transferProcessInitialized.getProviderPid())
                .format(transferRequestMessage.getFormat())
                .dataAddress(transferRequestMessage.getDataAddress())
                .state(TransferState.REQUESTED)
                .role(IConstants.ROLE_PROVIDER)
                .datasetId(transferProcessInitialized.getDatasetId())
                .created(transferProcessInitialized.getCreated())
                .createdBy(transferProcessInitialized.getCreatedBy())
                .modified(transferProcessInitialized.getModified())
                .lastModifiedBy(transferProcessInitialized.getLastModifiedBy())
                .version(transferProcessInitialized.getVersion())
                .build();
        transferProcessRepository.save(transferProcessRequested);
        publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_REQUESTED,
                "Transfer process requested",
                Map.of("role", IConstants.ROLE_PROTOCOL,
                        "transferProcess", transferProcessRequested,
                        "consumerPid", transferProcessRequested.getConsumerPid(),
                        "providerPid", transferProcessRequested.getProviderPid()));
        log.info("Requested TransferProcess created");
        return transferProcessRequested;
    }

    /**
     * Transfer from REQUESTED or SUSPENDED to STARTED state.
     *
     * @param transferStartMessage TransferStartMessage
     * @param consumerPid          consumerPid in case of consumer callback usage
     * @param providerPid          providerPid in case of provider usage
     * @return TransferProcess with status STARTED
     */
    public TransferProcess startDataTransfer(TransferStartMessage transferStartMessage, String consumerPid, String providerPid) {
        String consumerPidFinal = consumerPid == null ? transferStartMessage.getConsumerPid() : consumerPid;
        String providerPidFinal = providerPid == null ? transferStartMessage.getProviderPid() : providerPid;
        log.debug("Starting data transfer for consumerPid {} and providerPid {}", consumerPidFinal, providerPidFinal);

        TransferProcess transferProcessRequested = this.findTransferProcess(consumerPidFinal, providerPidFinal);

        if (TransferState.REQUESTED.equals(transferProcessRequested.getState()) && IConstants.ROLE_PROVIDER.equals(transferProcessRequested.getRole())) {
            // Only consumer can transit from REQUESTED to STARTED state
            String errorMessage = "State transition aborted, consumer can not transit from " + TransferState.REQUESTED.name()
                    + " to " + TransferState.STARTED.name();
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR,
                    "Transfer process state transition error",
                    Map.of("transferProcess", transferProcessRequested,
                            "currentState", transferProcessRequested.getState(),
                            "newState", TransferState.STARTED,
                            "consumerPid", transferProcessRequested.getConsumerPid(),
                            "providerPid", transferProcessRequested.getProviderPid(),
                            "role", IConstants.ROLE_PROTOCOL,
                            "errorMessage", errorMessage));
            throw new TransferProcessInvalidStateException(errorMessage, transferProcessRequested.getConsumerPid(), transferProcessRequested.getProviderPid());
        }

        stateTransitionCheck(transferProcessRequested, TransferState.STARTED);

        TransferProcess transferProcessStarted = TransferProcess.Builder.newInstance()
                .id(transferProcessRequested.getId())
                .agreementId(transferProcessRequested.getAgreementId())
                .consumerPid(transferProcessRequested.getConsumerPid())
                .providerPid(transferProcessRequested.getProviderPid())
                .callbackAddress(transferProcessRequested.getCallbackAddress())
                .dataAddress(transferStartMessage.getDataAddress())
                .format(transferProcessRequested.getFormat())
                .state(TransferState.STARTED)
                .role(transferProcessRequested.getRole())
                .datasetId(transferProcessRequested.getDatasetId())
                .created(transferProcessRequested.getCreated())
                .createdBy(transferProcessRequested.getCreatedBy())
                .modified(transferProcessRequested.getModified())
                .lastModifiedBy(transferProcessRequested.getLastModifiedBy())
                .version(transferProcessRequested.getVersion())
                .build();
        transferProcessRepository.save(transferProcessStarted);
        publisher.publishEvent(TransferProcessChangeEvent.Builder.newInstance()
                .oldTransferProcess(transferProcessRequested)
                .newTransferProcess(transferProcessStarted)
                .build());
        // TODO check how to handle this on consumer side!!!
        publisher.publishEvent(transferStartMessage);
        publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_STARTED,
                "Transfer process started",
                Map.of("role", IConstants.ROLE_PROTOCOL,
                        "transferProcess", transferProcessStarted,
                        "consumerPid", transferProcessStarted.getConsumerPid(),
                        "providerPid", transferProcessStarted.getProviderPid()));
        return transferProcessStarted;
    }

    /**
     * Find TransferProcess for given consumerPid and providerPid.
     *
     * @param consumerPid consumerPid to search by
     * @param providerPid providerPid to search by
     * @return TransferProcess
     */
    public TransferProcess findTransferProcess(String consumerPid, String providerPid) {
        return transferProcessRepository.findByConsumerPidAndProviderPid(consumerPid, providerPid)
                .orElseThrow(() ->
                {
                    publisher.publishEvent(
                            AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND,
                            "Transfer process with consumerPid " + consumerPid + " and providerPid " + providerPid + " not found",
                            Map.of("role", IConstants.ROLE_PROTOCOL,
                                    "consumerPid", IConstants.TEMPORARY_CONSUMER_PID,
                                    "providerPid", IConstants.TEMPORARY_PROVIDER_PID));
                    return new TransferProcessNotFoundException("Transfer process for consumerPid " + consumerPid
                            + " and providerPid " + providerPid + " not found");
                });
    }

    protected void stateTransitionCheck(TransferProcess transferProcess, TransferState stateToTransit) {
        if (!transferProcess.getState().canTransitTo(stateToTransit)) {
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR,
                    "Transfer process state transition error",
                    Map.of("transferProcess", transferProcess,
                            "currentState", transferProcess.getState(),
                            "newState", stateToTransit,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid(),
                            "role", IConstants.ROLE_PROTOCOL));
            throw new TransferProcessInvalidStateException("TransferProcess is in invalid state " + transferProcess.getState(),
                    transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        }
    }

    protected void checkSupportedFormats(TransferProcess transferProcess, String format) {
        String response = okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/"
                        + transferProcess.getDatasetId() + "/formats",
                HttpMethod.GET,
                null);

        Map<String, Object> details = new HashMap<>();
        details.put("role", IConstants.ROLE_PROTOCOL);
        details.put("transferProcess", transferProcess);
        if (transferProcess.getConsumerPid() != null) {
            details.put("consumerPid", transferProcess.getConsumerPid());
        }
        if (transferProcess.getProviderPid() != null) {
            details.put("providerPid", transferProcess.getProviderPid());
        }
        if (StringUtils.isBlank(response)) {
            publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_REQUESTED,
                    "Internal error while checking supported formats for dataset " + transferProcess.getDatasetId(),
                    details);
            throw new TransferProcessInternalException("Internal error",
                    transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        }

        TypeReference<GenericApiResponse<List<String>>> typeRef = new TypeReference<GenericApiResponse<List<String>>>() {
        };
        GenericApiResponse<List<String>> apiResp = TransferSerializer.deserializePlain(response, typeRef);
        boolean formatValid = apiResp.getData().stream().anyMatch(f -> f.equals(format));
        publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_REQUESTED,
                "Supported format evaluated as " + (formatValid ? "valid" : "invalid"),
                details);
        if (formatValid) {
            log.debug("Found supported format");
        } else {
            log.info("{} not found as one of supported distribution formats", format);
            throw new TransferProcessInvalidFormatException("dct:format '" + format + "' not supported",
                    transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        }
    }

}
