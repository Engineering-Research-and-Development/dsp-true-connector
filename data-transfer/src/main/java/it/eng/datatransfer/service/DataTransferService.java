package it.eng.datatransfer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import it.eng.datatransfer.event.TransferProcessChangeEvent;
import it.eng.datatransfer.exceptions.TransferProcessInternalException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidFormatException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DataTransferService {

    private final TransferProcessRepository transferProcessRepository;
    private final TransferRequestMessageRepository transferRequestMessageRepository;
    private final ApplicationEventPublisher publisher;

    private final OkHttpRestClient okHttpRestClient;

    public DataTransferService(TransferProcessRepository transferProcessRepository,
                               TransferRequestMessageRepository transferRequestMessageRepository,
                               ApplicationEventPublisher publisher,
                               OkHttpRestClient okHttpRestClient) {
        super();
        this.transferProcessRepository = transferProcessRepository;
        this.transferRequestMessageRepository = transferRequestMessageRepository;
        this.publisher = publisher;
        this.okHttpRestClient = okHttpRestClient;
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
     * Find transferProcess for given providerPid.
     *
     * @param providerPid providerPid to search by
     * @return TransferProcess
     */
    public TransferProcess findTransferProcessByProviderPid(String providerPid) {
        return transferProcessRepository.findByProviderPid(providerPid)
                .orElseThrow(() -> new TransferProcessNotFoundException("TransferProcess with providerPid " + providerPid + " not found"));
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
                    publisher.publishEvent(AuditEvent.Builder.newInstance()
                            .eventType(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND)
                            .description("Transfer process not found for agreementId " + transferRequestMessage.getAgreementId())
                            .details(Map.of("role", IConstants.ROLE_PROTOCOL,
                                    "transferRequestMessage", transferRequestMessage,
                                    "errorMessage", errorMessage))
                            .build());
                    return new TransferProcessNotFoundException(errorMessage);
                });

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
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_TRANSFER_REQUESTED)
                .description("Transfer process requested")
                .details(Map.of("role", IConstants.ROLE_PROTOCOL,
                        "transferProcess", transferProcessRequested,
                        "consumerPid", transferProcessRequested.getConsumerPid(),
                        "providerPid", transferProcessRequested.getProviderPid()))
                .build());
        log.info("Requested TransferProcess created");
        return transferProcessRequested;
    }

    private void checkSupportedFormats(TransferProcess transferProcess, String format) {
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
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .eventType(AuditEventType.PROTOCOL_TRANSFER_REQUESTED)
                    .description("Internal error while checking supported formats for dataset " + transferProcess.getDatasetId())
                    .details(details)
                    .build());
            throw new TransferProcessInternalException("Internal error",
                    transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        }

        TypeReference<GenericApiResponse<List<String>>> typeRef = new TypeReference<GenericApiResponse<List<String>>>() {
        };
        GenericApiResponse<List<String>> apiResp = TransferSerializer.deserializePlain(response, typeRef);
        boolean formatValid = apiResp.getData().stream().anyMatch(f -> f.equals(format));
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_TRANSFER_REQUESTED)
                .description("Supported format evaluated as " + (formatValid ? "valid" : "invalid"))
                .details(details)
                .build());
        if (formatValid) {
            log.debug("Found supported format");
        } else {
            log.info("{} not found as one of supported distribution formats", format);
            throw new TransferProcessInvalidFormatException("dct:format '" + format + "' not supported",
                    transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        }
//	    } catch (JsonProcessingException e) {
//	    	log.error(e.getLocalizedMessage(), e);
//	        throw new TransferProcessInternalException("Internal error", transferProcess.getConsumerPid(), transferProcess.getProviderPid());
//	    }
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

        TransferProcess transferProcessRequested = findTransferProcess(consumerPidFinal, providerPidFinal);

        if (IConstants.ROLE_PROVIDER.equals(transferProcessRequested.getRole()) && TransferState.REQUESTED.equals(transferProcessRequested.getState())) {
            // Only consumer can transit from REQUESTED to STARTED state
            String errorMessage = "State transition aborted, consumer can not transit from " + TransferState.REQUESTED.name()
                    + " to " + TransferState.STARTED.name();
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .eventType(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR)
                    .description("Transfer process state transition error")
                    .details(Map.of("transferProcess", transferProcessRequested,
                            "currentState", transferProcessRequested.getState(),
                            "newState", TransferState.STARTED,
                            "consumerPid", transferProcessRequested.getConsumerPid(),
                            "providerPid", transferProcessRequested.getProviderPid(),
                            "role", IConstants.ROLE_PROTOCOL,
                            "errorMessage", errorMessage))
                    .build());
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
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_TRANSFER_STARTED)
                .description("Transfer process started")
                .details(Map.of("role", IConstants.ROLE_PROTOCOL,
                        "transferProcess", transferProcessStarted,
                        "consumerPid", transferProcessStarted.getConsumerPid(),
                        "providerPid", transferProcessStarted.getProviderPid()))
                .build());
        return transferProcessStarted;
    }

    /**
     * Finds transfer process, check if status is correct, publish event and update state to COMPLETED.
     *
     * @param transferCompletionMessage TransferCompletionMessage
     * @param consumerPid               consumerPid in case of consumer callback usage
     * @param providerPid               providerPid in case of provider usage
     * @return TransferProcess with status COMPLETED
     */
    public TransferProcess completeDataTransfer(TransferCompletionMessage transferCompletionMessage, String consumerPid,
                                                String providerPid) {
        String consumerPidFinal = consumerPid == null ? transferCompletionMessage.getConsumerPid() : consumerPid;
        String providerPidFinal = providerPid == null ? transferCompletionMessage.getProviderPid() : providerPid;
        log.debug("Completing data transfer for consumerPid {} and providerPid {}", consumerPidFinal, providerPidFinal);

        TransferProcess transferProcessStarted = findTransferProcess(consumerPidFinal, providerPidFinal);
        stateTransitionCheck(transferProcessStarted, TransferState.COMPLETED);

        TransferProcess transferProcessCompleted = transferProcessStarted.copyWithNewTransferState(TransferState.COMPLETED);
        transferProcessRepository.save(transferProcessCompleted);
        publisher.publishEvent(TransferProcessChangeEvent.Builder.newInstance()
                .oldTransferProcess(transferProcessStarted)
                .newTransferProcess(transferProcessCompleted)
                .build());
        publisher.publishEvent(transferCompletionMessage);
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_TRANSFER_COMPLETED)
                .description("Transfer process completed")
                .details(Map.of("role", IConstants.ROLE_PROTOCOL,
                        "transferProcess", transferProcessCompleted,
                        "consumerPid", transferProcessCompleted.getConsumerPid(),
                        "providerPid", transferProcessCompleted.getProviderPid()))
                .build());
        return transferProcessCompleted;
    }

    /**
     * Transition data transfer to SUSPENDED state.
     *
     * @param transferSuspensionMessage message
     * @param consumerPid               consumerPid in case of consumer callback usage
     * @param providerPid               providerPid in case of provider usage
     * @return TransferProcess with status SUSPENDED
     */
    public TransferProcess suspendDataTransfer(TransferSuspensionMessage transferSuspensionMessage, String consumerPid,
                                               String providerPid) {
        String consumerPidFinal = consumerPid == null ? transferSuspensionMessage.getConsumerPid() : consumerPid;
        String providerPidFinal = providerPid == null ? transferSuspensionMessage.getProviderPid() : providerPid;
        log.debug("Suspending data transfer for consumerPid {} and providerPid {}", consumerPidFinal, providerPidFinal);

        TransferProcess transferProcessStarted = findTransferProcess(consumerPidFinal, providerPidFinal);
        stateTransitionCheck(transferProcessStarted, TransferState.SUSPENDED);

        TransferProcess transferProcessSuspended = transferProcessStarted.copyWithNewTransferState(TransferState.SUSPENDED);
        transferProcessRepository.save(transferProcessSuspended);
        publisher.publishEvent(TransferProcessChangeEvent.Builder.newInstance()
                .oldTransferProcess(transferProcessStarted)
                .newTransferProcess(transferProcessSuspended)
                .build());
        publisher.publishEvent(transferSuspensionMessage);
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED)
                .description("Transfer process completed")
                .details(Map.of("role", IConstants.ROLE_PROTOCOL,
                        "transferProcess", transferProcessSuspended,
                        "consumerPid", transferProcessSuspended.getConsumerPid(),
                        "providerPid", transferProcessSuspended.getProviderPid()))
                .build());
        return transferProcessSuspended;
    }

    /**
     * Transition data transfer to TERMINATED state.
     *
     * @param transferTerminationMessage message
     * @param consumerPid                consumerPid in case of consumer callback usage
     * @param providerPid                providerPid in case of provider usage
     * @return TransferProcess with status TERMINATED
     */
    public TransferProcess terminateDataTransfer(TransferTerminationMessage transferTerminationMessage, String consumerPid,
                                                 String providerPid) {
        String consumerPidFinal = consumerPid == null ? transferTerminationMessage.getConsumerPid() : consumerPid;
        String providerPidFinal = providerPid == null ? transferTerminationMessage.getProviderPid() : providerPid;
        log.debug("Terminating data transfer for consumerPid {} and providerPid {}", consumerPidFinal, providerPidFinal);

        // can be in any state except TERMINATED
        TransferProcess transferProcess = findTransferProcess(consumerPidFinal, providerPidFinal);
        stateTransitionCheck(transferProcess, TransferState.TERMINATED);

        TransferProcess transferProcessTerminated = transferProcess.copyWithNewTransferState(TransferState.TERMINATED);
        transferProcessRepository.save(transferProcessTerminated);
        publisher.publishEvent(TransferProcessChangeEvent.Builder.newInstance()
                .oldTransferProcess(transferProcess)
                .newTransferProcess(transferProcessTerminated)
                .build());
        publisher.publishEvent(transferTerminationMessage);
        publisher.publishEvent(AuditEvent.Builder.newInstance()
                .eventType(AuditEventType.PROTOCOL_TRANSFER_TERMINATED)
                .description("Transfer process completed")
                .details(Map.of("role", IConstants.ROLE_PROTOCOL,
                        "transferProcess", transferProcessTerminated,
                        "consumerPid", transferProcessTerminated.getConsumerPid(),
                        "providerPid", transferProcessTerminated.getProviderPid()))
                .build());
        return transferProcessTerminated;
    }

    /**
     * Find TransferProcess by consumerPid and providerPid.
     *
     * @param consumerPid consumerPid to search by
     * @param providerPid providerPid to search by
     * @return TransferProcess if found, otherwise throws TransferProcessNotFoundException
     */
    public TransferProcess findTransferProcess(String consumerPid, String providerPid) {
        return transferProcessRepository.findByConsumerPidAndProviderPid(consumerPid, providerPid)
                .orElseThrow(() ->
                {
                    publisher.publishEvent(AuditEvent.Builder.newInstance()
                            .description("Transfer process with consumerPid " + consumerPid + " and providerPid " + providerPid + " not found")
                            .eventType(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND)
                            .details(Map.of("role", IConstants.ROLE_PROTOCOL,
                                    "consumerPid", IConstants.TEMPORARY_CONSUMER_PID,
                                    "providerPid", IConstants.TEMPORARY_PROVIDER_PID))
                            .build());
                    return new TransferProcessNotFoundException("Transfer process for consumerPid " + consumerPid
                            + " and providerPid " + providerPid + " not found");
                });
    }

    private void stateTransitionCheck(TransferProcess transferProcess, TransferState stateToTransit) {
        if (!transferProcess.getState().canTransitTo(stateToTransit)) {
            publisher.publishEvent(AuditEvent.Builder.newInstance()
                    .eventType(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR)
                    .description("Transfer process state transition error")
                    .details(Map.of("transferProcess", transferProcess,
                            "currentState", transferProcess.getState(),
                            "newState", stateToTransit,
                            "consumerPid", transferProcess.getConsumerPid(),
                            "providerPid", transferProcess.getProviderPid(),
                            "role", IConstants.ROLE_PROTOCOL))
                    .build());
            throw new TransferProcessInvalidStateException("TransferProcess is in invalid state " + transferProcess.getState(),
                    transferProcess.getConsumerPid(), transferProcess.getProviderPid());
        }
    }

}
