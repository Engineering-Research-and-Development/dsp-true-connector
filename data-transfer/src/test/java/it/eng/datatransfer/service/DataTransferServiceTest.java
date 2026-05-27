package it.eng.datatransfer.service;

import it.eng.datatransfer.exceptions.TransferProcessInternalException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidFormatException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.model.DataAddress;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.datatransfer.router.DataPlaneRouter;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.service.AuditEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DataTransferServiceTest {

    @Mock
    private TransferProcessRepository transferProcessRepository;
    @Mock
    private TransferRequestMessageRepository transferRequestMessageRepository;
    @Mock
    private AuditEventPublisher publisher;
    @Mock
    private OkHttpRestClient okHttpRestClient;
    @Mock
    private DataTransferProperties transferProperties;
    @Mock
    private TemporaryBucketUserService temporaryBucketUserService;
    @Mock
    private DataPlaneRouter dataPlaneRouter;

    @InjectMocks
    private DataTransferService service;

    @Captor
    private ArgumentCaptor<TransferProcess> argTransferProcess;
    @Captor
    private ArgumentCaptor<AuditEventType> eventTypeCaptor;
    @Captor
    private ArgumentCaptor<Map<String, Object>> argCaptorAuditEventDetails;

    @Test
    @DisplayName("Data transfer exists and state is started")
    public void dataTransferExistsAndStarted() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));
        assertTrue(service.isDataTransferStarted(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID));
    }

    @Test
    @DisplayName("Data transfer exists and state is not started")
    public void dataTransferExistsAndNotStarted() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));
        assertFalse(service.isDataTransferStarted(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID));
    }

    @Test
    @DisplayName("Data transfer not found")
    public void dataTransferDoesNotExists() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID))
                .thenReturn(Optional.empty());
        assertThrows(TransferProcessNotFoundException.class,
                () -> service.isDataTransferStarted(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID));
    }

    @Test
    @DisplayName("Find TransferProcess by providerPid")
    public void getTransferProcessByProviderPid() {
        when(transferProcessRepository.findByProviderPid(DataTransferMockObjectUtil.PROVIDER_PID)).thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));
        TransferProcess tp = service.findTransferProcessByProviderPid(DataTransferMockObjectUtil.PROVIDER_PID);
        assertNotNull(tp);
    }

    @Test
    @DisplayName("TransferProcess by providerPid not found")
    public void transferProcessByProviderPid_NotFound() {
        when(transferProcessRepository.findByProviderPid(DataTransferMockObjectUtil.PROVIDER_PID)).thenReturn(Optional.empty());
        assertThrows(TransferProcessNotFoundException.class,
                () -> service.findTransferProcessByProviderPid(DataTransferMockObjectUtil.PROVIDER_PID));
    }

    @Test
    @DisplayName("Find TransferProcess by consumerPid")
    public void getTransferProcessByConsumerPid() {
        when(transferProcessRepository.findByConsumerPid(DataTransferMockObjectUtil.CONSUMER_PID))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));
        TransferProcess tp = service.findTransferProcessByConsumerPid(DataTransferMockObjectUtil.CONSUMER_PID);
        assertNotNull(tp);
        assertEquals(DataTransferMockObjectUtil.CONSUMER_PID, tp.getConsumerPid());
    }

    @Test
    @DisplayName("TransferProcess by consumerPid not found")
    public void transferProcessByConsumerPid_NotFound() {
        when(transferProcessRepository.findByConsumerPid(DataTransferMockObjectUtil.CONSUMER_PID))
                .thenReturn(Optional.empty());
        assertThrows(TransferProcessNotFoundException.class,
                () -> service.findTransferProcessByConsumerPid(DataTransferMockObjectUtil.CONSUMER_PID));
    }

    @Test
    @DisplayName("DataTransfer requested - success")
    public void initiateTransferProcess() {
        when(transferProcessRepository.findByAgreementId(DataTransferMockObjectUtil.AGREEMENT_ID)).thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED));

        List<String> formats = new ArrayList<>();
        formats.add(DataTransferFormat.HTTP_PULL.name());
        GenericApiResponse<List<String>> resp = GenericApiResponse.success(formats, "Ok");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(resp));

        TransferProcess transferProcessRequested = service.initiateDataTransfer(DataTransferMockObjectUtil.TRANSFER_REQUEST_MESSAGE);

        assertNotNull(transferProcessRequested);
        assertEquals(TransferState.REQUESTED, transferProcessRequested.getState());
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals(TransferState.REQUESTED, argTransferProcess.getValue().getState());
        verify(transferRequestMessageRepository).save(DataTransferMockObjectUtil.TRANSFER_REQUEST_MESSAGE);

        verify(publisher, times(2)).publishEvent(eventTypeCaptor.capture(), any(String.class), argCaptorAuditEventDetails.capture());
    }

    @Test
    @DisplayName("DataTransfer requested - fail - dct:format")
    public void initiateTransferProcess_format_not_supported() {
        when(transferProcessRepository.findByAgreementId(DataTransferMockObjectUtil.AGREEMENT_ID))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED));

        List<String> formats = new ArrayList<>();
        formats.add("ABC");
        GenericApiResponse<List<String>> resp = GenericApiResponse.success(formats, "Ok");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(resp));

        assertThrows(TransferProcessInvalidFormatException.class,
                () -> service.initiateDataTransfer(DataTransferMockObjectUtil.TRANSFER_REQUEST_MESSAGE));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_REQUESTED);
    }

    @Test
    @DisplayName("DataTransfer requested - fail - dct:format - null")
    public void initiateTransferProcess_format_null() {
        when(transferProcessRepository.findByAgreementId(DataTransferMockObjectUtil.AGREEMENT_ID)).thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED));

        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(null);

        assertThrows(TransferProcessInternalException.class,
                () -> service.initiateDataTransfer(DataTransferMockObjectUtil.TRANSFER_REQUEST_MESSAGE));

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_REQUESTED);
    }

    @Test
    @DisplayName("DataTransfer requested - initialized TransferProcess does not exist")
    public void initiateTransferProcess_exists() {
        when(transferProcessRepository.findByAgreementId(DataTransferMockObjectUtil.AGREEMENT_ID))
                .thenReturn(Optional.empty());
        assertThrows(TransferProcessNotFoundException.class,
                () -> service.initiateDataTransfer(DataTransferMockObjectUtil.TRANSFER_REQUEST_MESSAGE));

        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND);
    }

    @Test
    @DisplayName("DataTransfer requested - state transition error - TransferProcess not in INITIALIZED state")
    public void initiateTransferProcess_invalidState() {
        when(transferProcessRepository.findByAgreementId(DataTransferMockObjectUtil.AGREEMENT_ID))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        assertThrows(TransferProcessInvalidStateException.class,
                () -> service.initiateDataTransfer(DataTransferMockObjectUtil.TRANSFER_REQUEST_MESSAGE));

        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());
        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR);
    }

    @Test
    @DisplayName("DataTransfer requested - HTTP_PUSH - endpoint properties are stored as-is (no encryption)")
    public void initiateTransferProcess_httpPush_storesEndpointPropertiesAsIs() {
        String secretKey = "plain-secret-key";

        DataAddress httpPushDataAddress = DataAddress.Builder.newInstance()
                .endpointProperties(List.of(
                        EndpointProperty.Builder.newInstance().name("bucketName").value("my-bucket").build(),
                        EndpointProperty.Builder.newInstance().name("accessKey").value("access-key").build(),
                        EndpointProperty.Builder.newInstance().name("secretKey").value(secretKey).build(),
                        EndpointProperty.Builder.newInstance().name("endpointOverride").value("http://minio:9000").build()
                ))
                .build();

        TransferRequestMessage httpPushRequest = TransferRequestMessage.Builder.newInstance()
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .format(DataTransferFormat.HTTP_PUSH.format())
                .dataAddress(httpPushDataAddress)
                .build();

        when(transferProcessRepository.findByAgreementId(DataTransferMockObjectUtil.AGREEMENT_ID))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED));

        List<String> formats = new ArrayList<>();
        formats.add(DataTransferFormat.HTTP_PUSH.format());
        GenericApiResponse<List<String>> resp = GenericApiResponse.success(formats, "Ok");
        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(TransferSerializer.serializePlain(resp));

        service.initiateDataTransfer(httpPushRequest);

        verify(transferProcessRepository).save(argTransferProcess.capture());
        TransferProcess saved = argTransferProcess.getValue();
        String storedSecretKey = saved.getDataAddress().getEndpointProperties().stream()
                .filter(p -> "secretKey".equals(p.getName()))
                .findFirst()
                .map(EndpointProperty::getValue)
                .orElse(null);
        assertEquals(secretKey, storedSecretKey,
                "secretKey must be stored as-is — encryption is the DP's responsibility");
    }

    // TransferStartMessage
    @Test
    @DisplayName("StartDataTransfer from REQUESTED - provider")
    public void startDataTransfer_fromRequested_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        assertThrows(TransferProcessInvalidStateException.class,
                () -> service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR);
    }

    @Test
    @DisplayName("StartDataTransfer from REQUESTED - consumer callback")
    public void startDataTransfer_fromRequested_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER));

        TransferProcess transferProcessStarted = service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE, DataTransferMockObjectUtil.CONSUMER_PID, null);

        assertEquals(TransferState.STARTED, transferProcessStarted.getState());
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals(TransferState.STARTED, argTransferProcess.getValue().getState());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STARTED);
    }

    @Test
    @DisplayName("StartDataTransfer from REQUESTED - consumer callback preserves existing HTTP-PUSH dataAddress when start message has none")
    public void startDataTransfer_fromRequested_consumer_httpPushPreservesExistingDataAddress() {
        DataAddress pushDataAddress = DataAddress.Builder.newInstance()
                .endpointProperties(List.of(
                        EndpointProperty.Builder.newInstance().name("bucketName").value("consumer-bucket").build(),
                        EndpointProperty.Builder.newInstance().name("objectKey").value("tp-1").build(),
                        EndpointProperty.Builder.newInstance().name("accessKey").value("access").build(),
                        EndpointProperty.Builder.newInstance().name("secretKey").value("secret").build()))
                .build();

        TransferProcess requestedConsumerPush = TransferProcess.Builder.newInstance()
                .id(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER.getId())
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(pushDataAddress)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .format(DataTransferFormat.HTTP_PUSH.format())
                .state(TransferState.REQUESTED)
                .role(IConstants.ROLE_CONSUMER)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .modified(DataTransferMockObjectUtil.MODIFIED)
                .build();

        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(requestedConsumerPush));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransferProcess transferProcessStarted = service.startDataTransfer(
                DataTransferMockObjectUtil.TRANSFER_START_MESSAGE,
                DataTransferMockObjectUtil.CONSUMER_PID,
                null);

        assertEquals(TransferState.STARTED, transferProcessStarted.getState());
        assertNotNull(transferProcessStarted.getDataAddress());
        assertEquals("consumer-bucket", transferProcessStarted.getDataAddress().getEndpointProperties().stream()
                .filter(p -> "bucketName".equals(p.getName()))
                .findFirst()
                .map(EndpointProperty::getValue)
                .orElse(null));
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertNotNull(argTransferProcess.getValue().getDataAddress());
        assertEquals("consumer-bucket", argTransferProcess.getValue().getDataAddress().getEndpointProperties().stream()
                .filter(p -> "bucketName".equals(p.getName()))
                .findFirst()
                .map(EndpointProperty::getValue)
                .orElse(null));
    }

    @Test
    @DisplayName("StartDataTransfer from SUSPENDED - provider")
    public void startDataTransfer_fromSuspended_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER));

        TransferProcess transferProcessStarted = service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID);

        assertEquals(TransferState.STARTED, transferProcessStarted.getState());
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals(TransferState.STARTED, argTransferProcess.getValue().getState());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STARTED);
    }

    @Test
    @DisplayName("StartDataTransfer from SUSPENDED - consumer callback")
    public void startDataTransfer_fromSuspended_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_CONSUMER));

        TransferProcess transferProcessStarted = service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE, DataTransferMockObjectUtil.CONSUMER_PID, null);

        assertEquals(TransferState.STARTED, transferProcessStarted.getState());
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals(TransferState.STARTED, argTransferProcess.getValue().getState());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STARTED);
    }

    @Test
    @DisplayName("StartDataTransfer - transfer process not found - provider")
    public void startDataTransfer_tpNotFound_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND);
    }

    @Test
    @DisplayName("StartDataTransfer - transfer process not found - consumer callback")
    public void startDataTransfer_tpNotFound_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE, DataTransferMockObjectUtil.CONSUMER_PID, null));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND);
    }

    @Test
    @DisplayName("StartDataTransfer - invalid state")
    public void startDataTransfer_invalidState() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        assertThrows(TransferProcessInvalidStateException.class,
                () -> service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR);
    }

    // TransferCompletionMessage
    @Test
    @DisplayName("TransferCompletionMessage from STARTED - provider")
    public void completeDataTransfer_fromStarted_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        TransferProcess transferProcessCompleted = service.completeDataTransfer(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID);

        assertEquals(TransferState.COMPLETED, transferProcessCompleted.getState());
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals(TransferState.COMPLETED, argTransferProcess.getValue().getState());
        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_COMPLETED);
    }

    @Test
    @DisplayName("TransferCompletionMessage from STARTED - consumer callback")
    public void completeDataTransfer_fromStarted_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        TransferProcess transferProcessCompleted = service.completeDataTransfer(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE,
                DataTransferMockObjectUtil.CONSUMER_PID, null);

        assertEquals(TransferState.COMPLETED, transferProcessCompleted.getState());
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals(TransferState.COMPLETED, argTransferProcess.getValue().getState());
        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_COMPLETED);
    }

    @Test
    @DisplayName("TransferCompletionMessage - transfer process not found - provider")
    public void completeDataTransfer_tpNotFound_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.completeDataTransfer(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND);
    }

    @Test
    @DisplayName("TransferCompletionMessage - transfer process not found - consumer callback")
    public void completeDataTransfer_tpNotFound_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.completeDataTransfer(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE, DataTransferMockObjectUtil.CONSUMER_PID, null));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND);
    }

    @Test
    @DisplayName("TransferCompletionMessage - invalid state")
    public void completeDataTransfer_invalidState() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        assertThrows(TransferProcessInvalidStateException.class,
                () -> service.completeDataTransfer(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR);
    }

    @Test
    @DisplayName("HTTP-PUSH consumer completion cleans up temporary IAM credentials")
    public void completeDataTransfer_httpPush_consumer_deletesTemporaryUser() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_CONSUMER_HTTP_PUSH));

        service.completeDataTransfer(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE,
                DataTransferMockObjectUtil.CONSUMER_PID, null);

        verify(temporaryBucketUserService).deleteTemporaryUser(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_CONSUMER_HTTP_PUSH.getId());
    }

    @Test
    @DisplayName("completeDataTransfer preserves transportProfile on the completed process")
    public void completeDataTransfer_preservesTransportProfile() {
        TransferProcess startedWithProfile = TransferProcess.Builder.newInstance()
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .state(TransferState.STARTED)
                .format(DataTransferFormat.HTTP_PULL.name())
                .transportProfile("stream:grpc")
                .build();
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(), any()))
                .thenReturn(Optional.of(startedWithProfile));

        service.completeDataTransfer(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE,
                null, DataTransferMockObjectUtil.PROVIDER_PID);

        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals("stream:grpc", argTransferProcess.getValue().getTransportProfile(),
                "transportProfile must be preserved on the completed TransferProcess");
    }

    @Test
    @DisplayName("completeDataTransfer clears sticky assignment for the completed process")
    public void completeDataTransfer_clearsStickyAssignment() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(), any()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        service.completeDataTransfer(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE,
                null, DataTransferMockObjectUtil.PROVIDER_PID);

        verify(dataPlaneRouter).clearStickyAssignment(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
    }

    @Test
    @DisplayName("terminateDataTransfer clears sticky assignment for the terminated process")
    public void terminateDataTransfer_clearsStickyAssignment() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(), any()))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        service.terminateDataTransfer(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE,
                null, DataTransferMockObjectUtil.PROVIDER_PID);

        verify(dataPlaneRouter).clearStickyAssignment(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED.getId());
    }

    @Test
    @DisplayName("HTTP-PUSH consumer termination cleans up temporary IAM credentials")
    public void terminateDataTransfer_httpPush_consumer_deletesTemporaryUser() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_CONSUMER_HTTP_PUSH));

        service.terminateDataTransfer(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE,
                DataTransferMockObjectUtil.CONSUMER_PID, null);

        verify(temporaryBucketUserService).deleteTemporaryUser(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED_CONSUMER_HTTP_PUSH.getId());
    }

    // suspend
    @Test
    @DisplayName("TransferSuspensionMessage from STARTED - provider")
    public void suspendDataTransfer_fromStarted_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));
        when(transferProcessRepository.save(any(TransferProcess.class))).thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER);
        
        TransferProcess transferProcessSuspended = service.suspendDataTransfer(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE,
                null, DataTransferMockObjectUtil.PROVIDER_PID);

        assertEquals(TransferState.SUSPENDED, transferProcessSuspended.getState());
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals(TransferState.SUSPENDED, argTransferProcess.getValue().getState());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED);
    }

    @Test
    @DisplayName("TransferSuspensionMessage from STARTED - consumer callback")
    public void suspendDataTransfer_fromStarted_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenReturn(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER);

        TransferProcess transferProcessSuspended = service.suspendDataTransfer(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE,
                DataTransferMockObjectUtil.CONSUMER_PID, null);

        assertEquals(TransferState.SUSPENDED, transferProcessSuspended.getState());
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals(TransferState.SUSPENDED, argTransferProcess.getValue().getState());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED);
    }

    @Test
    @DisplayName("TransferSuspensionMessage - transfer process not found - provider")
    public void suspendDataTransfer_tpNotFound_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.suspendDataTransfer(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND);
    }

    @Test
    @DisplayName("TransferSuspensionMessage - transfer process not found - consumer callback")
    public void suspendDataTransfer_tpNotFound_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.suspendDataTransfer(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE, DataTransferMockObjectUtil.CONSUMER_PID, null));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND);
    }

    @Test
    @DisplayName("TransferSuspensionMessage - invalid state")
    public void suspendDataTransfer_invalidState() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        assertThrows(TransferProcessInvalidStateException.class,
                () -> service.suspendDataTransfer(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR);
    }

    private static Stream<Arguments> provideTransferProcess() {
        return Stream.of(
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER)
        );
    }

    // terminate
    @DisplayName("TransferTerminationMessage - provider")
    @ParameterizedTest
    @MethodSource("provideTransferProcess")
    public void terminateDataTransfer_provider(TransferProcess input) {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(input));

        TransferProcess transferProcessSuspended = service.terminateDataTransfer(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE,
                null, DataTransferMockObjectUtil.PROVIDER_PID);

        assertEquals(TransferState.TERMINATED, transferProcessSuspended.getState());
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals(TransferState.TERMINATED, argTransferProcess.getValue().getState());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_TERMINATED);
    }

    @DisplayName("TransferTerminationMessage - consumer callback")
    @ParameterizedTest
    @MethodSource("provideTransferProcess")
    public void terminateDataTransfer_consumer(TransferProcess input) {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(input));

        TransferProcess transferProcessSuspended = service.terminateDataTransfer(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE,
                DataTransferMockObjectUtil.CONSUMER_PID, null);

        assertEquals(TransferState.TERMINATED, transferProcessSuspended.getState());
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals(TransferState.TERMINATED, argTransferProcess.getValue().getState());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_TERMINATED);
    }

    @Test
    @DisplayName("TransferTerminationMessage - transfer process not found - provider")
    public void terminateDataTransfer_tpNotFound_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.terminateDataTransfer(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND);
    }

    @Test
    @DisplayName("TransferTerminationMessage - transfer process not found - consumer callback")
    public void terminateDataTransfer_tpNotFound_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.terminateDataTransfer(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE, DataTransferMockObjectUtil.CONSUMER_PID, null));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND);
    }

    private static Stream<Arguments> provideInvalidTransferProcess() {
        return Stream.of(
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_COMPLETED),
                Arguments.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_TERMINATED)
        );
    }

    @DisplayName("TransferTerminationMessage - invalid state")
    @ParameterizedTest
    @MethodSource("provideInvalidTransferProcess")
    public void terminateDataTransfer_invalidState(TransferProcess input) {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(input));

        assertThrows(TransferProcessInvalidStateException.class,
                () -> service.terminateDataTransfer(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verifyAuditEvent(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR);
    }

    /**
     * Verifies that exactly one 3-argument audit event of the given type was published.
     *
     * @param eventType the expected {@link AuditEventType} that should have been published
     */
    private void verifyAuditEvent(AuditEventType eventType) {
        verify(publisher).publishEvent(eventTypeCaptor.capture(), any(String.class), argCaptorAuditEventDetails.capture());
        assertEquals(eventType, eventTypeCaptor.getValue());
        assertNotNull(argCaptorAuditEventDetails.getValue());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Task 5 – CP gRPC orchestration
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startDataTransfer - consumer gRPC with automaticTransfer enabled fires AutoTransferDownloadEvent")
    public void startDataTransfer_grpc_consumer_autoDownloadFires() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER_GRPC));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transferProperties.isAutomaticTransfer()).thenReturn(true);

        service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE,
                DataTransferMockObjectUtil.CONSUMER_PID, null);

        // AutoTransferDownloadEvent must be published for stream:grpc consumer start
        verify(publisher).publishEvent((Object) argThat(evt ->
                evt instanceof it.eng.datatransfer.event.AutoTransferDownloadEvent));
    }

    @Test
    @DisplayName("startDataTransfer - consumer gRPC with automaticTransfer disabled does NOT fire AutoTransferDownloadEvent")
    public void startDataTransfer_grpc_consumer_autoDownloadNotFiredWhenDisabled() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER_GRPC));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transferProperties.isAutomaticTransfer()).thenReturn(false);

        service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE,
                DataTransferMockObjectUtil.CONSUMER_PID, null);

        // No AutoTransferDownloadEvent when automatic transfer is disabled
        verify(publisher, never()).publishEvent((Object) argThat(evt ->
                evt instanceof it.eng.datatransfer.event.AutoTransferDownloadEvent));
    }

    @Test
    @DisplayName("startDataTransfer - consumer Kafka with automaticTransfer enabled fires AutoTransferDownloadEvent")
    public void startDataTransfer_kafka_consumer_autoDownloadFires() {
        TransferProcess requestedConsumerKafka = TransferProcess.Builder.newInstance()
                .id(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_CONSUMER.getId())
                .agreementId(DataTransferMockObjectUtil.AGREEMENT_ID)
                .consumerPid(DataTransferMockObjectUtil.CONSUMER_PID)
                .providerPid(DataTransferMockObjectUtil.PROVIDER_PID)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .callbackAddress(DataTransferMockObjectUtil.CALLBACK_ADDRESS)
                .format("stream:kafka")
                .state(TransferState.REQUESTED)
                .role(IConstants.ROLE_CONSUMER)
                .datasetId(DataTransferMockObjectUtil.DATASET_ID)
                .tenantId(DataTransferMockObjectUtil.TENANT_ID)
                .modified(DataTransferMockObjectUtil.MODIFIED)
                .build();
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(requestedConsumerKafka));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transferProperties.isAutomaticTransfer()).thenReturn(true);

        service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE,
                DataTransferMockObjectUtil.CONSUMER_PID, null);

        verify(publisher).publishEvent((Object) argThat(evt ->
                evt instanceof it.eng.datatransfer.event.AutoTransferDownloadEvent));
    }
}
