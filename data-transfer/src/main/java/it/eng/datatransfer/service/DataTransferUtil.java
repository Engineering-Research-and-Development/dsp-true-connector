package it.eng.datatransfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.datatransfer.event.TransferProcessChangeEvent;
import it.eng.datatransfer.exceptions.*;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.datatransfer.rest.protocol.DataTransferCallback;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.util.CredentialUtils;
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
    private final S3Properties s3Properties;
    private final CredentialUtils credentialUtils;
    private final DataTransferProperties dataTransferProperties;

    private final ObjectMapper mapper = new ObjectMapper();

    public DataTransferUtil(OkHttpClient okHttpClient, TransferProcessRepository transferProcessRepository, TransferRequestMessageRepository transferRequestMessageRepository, AuditEventPublisher publisher, OkHttpRestClient okHttpRestClient, S3Properties s3Properties, CredentialUtils credentialUtils, DataTransferProperties dataTransferProperties) {
        this.okHttpClient = okHttpClient;
        this.transferProcessRepository = transferProcessRepository;
        this.transferRequestMessageRepository = transferRequestMessageRepository;
        this.publisher = publisher;
        this.okHttpRestClient = okHttpRestClient;
        this.s3Properties = s3Properties;
        this.credentialUtils = credentialUtils;
        this.dataTransferProperties = dataTransferProperties;
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


    /// AS CONSUMER
    public JsonNode requestTransfer(TransferProcess transferProcessInitialized, String format) {
        stateTransitionCheck(transferProcessInitialized, TransferState.REQUESTED);

        TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
                .agreementId(transferProcessInitialized.getAgreementId())
                .callbackAddress(dataTransferProperties.consumerCallbackAddress())
                .consumerPid(transferProcessInitialized.getConsumerPid())
                .format(format)
                .dataAddress(null)
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
                        .format(format)
                        .dataAddress(null)
                        .isDownloaded(transferProcessInitialized.isDownloaded())
                        .dataId(transferProcessInitialized.getDataId())
                        .callbackAddress(transferProcessInitialized.getCallbackAddress())
                        .role(IConstants.ROLE_CONSUMER)
                        .state(transferProcessFromResponse.getState())
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
            } catch (JsonProcessingException ex) {
                throw new DataTransferAPIException("Error occurred");
            }
        }
        return TransferSerializer.serializePlainJsonNode(transferProcessForDB);
    }

    public TransferProcess startDataTransfer(TransferStartMessage transferStartMessage, String consumerPid, String providerPid) {
        String consumerPidFinal = consumerPid == null ? transferStartMessage.getConsumerPid() : consumerPid;
        String providerPidFinal = providerPid == null ? transferStartMessage.getProviderPid() : providerPid;
        log.debug("Starting data transfer for consumerPid {} and providerPid {}", consumerPidFinal, providerPidFinal);

        TransferProcess transferProcessRequested = findTransferProcess(consumerPidFinal, providerPidFinal);

        if (IConstants.ROLE_PROVIDER.equals(transferProcessRequested.getRole()) && TransferState.REQUESTED.equals(transferProcessRequested.getState())) {
            // Only consumer can transit from REQUESTED to STARTED state
            String errorMessage = "State transition aborted, consumer can not transit from " + TransferState.REQUESTED.name()
                    + " to " + TransferState.STARTED.name();
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

    public TransferProcess findTransferProcess(String consumerPid, String providerPid) {
        return transferProcessRepository.findByConsumerPidAndProviderPid(consumerPid, providerPid)
                .orElseThrow(() -> new TransferProcessNotFoundException("Transfer process for consumerPid " + consumerPid
                        + " and providerPid " + providerPid + " not found")
                );
    }
}
