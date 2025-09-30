package it.eng.datatransfer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.exceptions.TransferProcessInternalException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidFormatException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.model.TransferError;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
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
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DataTransferUtil {

    private final OkHttpClient okHttpClient;
    private final TransferProcessRepository transferProcessRepository;
    private final TransferRequestMessageRepository transferRequestMessageRepository;
    private final AuditEventPublisher publisher;
    private final OkHttpRestClient okHttpRestClient;

    public DataTransferUtil(OkHttpClient okHttpClient, TransferProcessRepository transferProcessRepository, TransferRequestMessageRepository transferRequestMessageRepository, AuditEventPublisher publisher, OkHttpRestClient okHttpRestClient) {
        this.okHttpClient = okHttpClient;
        this.transferProcessRepository = transferProcessRepository;
        this.transferRequestMessageRepository = transferRequestMessageRepository;
        this.publisher = publisher;
        this.okHttpRestClient = okHttpRestClient;
    }

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

    private void stateTransitionCheck(TransferProcess transferProcess, TransferState stateToTransit) {
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
//	    } catch (JsonProcessingException e) {
//	    	log.error(e.getLocalizedMessage(), e);
//	        throw new TransferProcessInternalException("Internal error", transferProcess.getConsumerPid(), transferProcess.getProviderPid());
//	    }
    }

    public void sendErrorMessage(String consumerPid, String providerPid, String targetAddress) {
        TransferError transferError = TransferError.Builder.newInstance()
                .consumerPid(consumerPid)
                .providerPid(providerPid)
                .code("tck-error")
                .build();
        Request.Builder requestBuilder = new Request.Builder().url(targetAddress);
        RequestBody body;
        JsonNode jsonNode = TransferSerializer.serializePlainJsonNode(transferError);
        if (jsonNode != null) {
            body = RequestBody.create(jsonNode.toPrettyString(), MediaType.parse("application/json"));
        } else {
            body = RequestBody.create("", MediaType.parse("application/json"));
        }
        requestBuilder.post(body);
        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            int code = response.code();
            log.info("Status {}", code);
            //why is this not JSONNode
            String resp = null;
            if (response.body() != null) {
                resp = response.body().string();
            }
            log.info("Response received: {}", resp);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
