package it.eng.datatransfer.service;

import it.eng.datatransfer.exceptions.TransferProcessInternalException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidFormatException;
import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.exceptions.TransferProcessNotFoundException;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.response.GenericApiResponse;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;
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
    private ApplicationEventPublisher publisher;
    @Mock
    private GenericApiResponse<String> apiResponse;
    @Mock
    private OkHttpRestClient okHttpRestClient;

    @InjectMocks
    private DataTransferService service;

    @Captor
    private ArgumentCaptor<TransferProcess> argTransferProcess;
    @Captor
    private ArgumentCaptor<AuditEvent> argAuditEvent;

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
        assertFalse(service.isDataTransferStarted(DataTransferMockObjectUtil.CONSUMER_PID, DataTransferMockObjectUtil.PROVIDER_PID));
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

        verify(publisher, times(2)).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_REQUESTED, auditEvent.getEventType());
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

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_REQUESTED, auditEvent.getEventType());
    }

    @Test
    @DisplayName("DataTransfer requested - fail - dct:format - null")
    public void initiateTransferProcess_format_null() {
        when(transferProcessRepository.findByAgreementId(DataTransferMockObjectUtil.AGREEMENT_ID)).thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_INITIALIZED));

        when(okHttpRestClient.sendInternalRequest(any(String.class), any(HttpMethod.class), isNull()))
                .thenReturn(null);

        assertThrows(TransferProcessInternalException.class,
                () -> service.initiateDataTransfer(DataTransferMockObjectUtil.TRANSFER_REQUEST_MESSAGE));

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_REQUESTED, auditEvent.getEventType());
    }

    @Test
    @DisplayName("DataTransfer requested - initialized TransferProcess does not exist")
    public void initiateTransferProcess_exists() {
        when(transferProcessRepository.findByAgreementId(DataTransferMockObjectUtil.AGREEMENT_ID))
                .thenReturn(Optional.empty());
        assertThrows(TransferProcessNotFoundException.class,
                () -> service.initiateDataTransfer(DataTransferMockObjectUtil.TRANSFER_REQUEST_MESSAGE));

        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, auditEvent.getEventType());
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

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR, auditEvent.getEventType());
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

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_STARTED, auditEvent.getEventType());
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

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_STARTED, auditEvent.getEventType());
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

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_STARTED, auditEvent.getEventType());
    }

    @Test
    @DisplayName("StartDataTransfer - transfer process not found - provider")
    public void startDataTransfer_tpNotFound_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, auditEvent.getEventType());
    }

    @Test
    @DisplayName("StartDataTransfer - transfer process not found - consumer callback")
    public void startDataTransfer_tpNotFound_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE, DataTransferMockObjectUtil.CONSUMER_PID, null));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, auditEvent.getEventType());
    }

    @Test
    @DisplayName("StartDataTransfer - invalid state")
    public void startDataTransfer_invalidState() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        assertThrows(TransferProcessInvalidStateException.class,
                () -> service.startDataTransfer(DataTransferMockObjectUtil.TRANSFER_START_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR, auditEvent.getEventType());
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

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_COMPLETED, auditEvent.getEventType());
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

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_COMPLETED, auditEvent.getEventType());
    }

    @Test
    @DisplayName("TransferCompletionMessage - transfer process not found - provider")
    public void completeDataTransfer_tpNotFound_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.completeDataTransfer(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, auditEvent.getEventType());
    }

    @Test
    @DisplayName("TransferCompletionMessage - transfer process not found - consumer callback")
    public void completeDataTransfer_tpNotFound_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.completeDataTransfer(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE, DataTransferMockObjectUtil.CONSUMER_PID, null));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, auditEvent.getEventType());
    }

    @Test
    @DisplayName("TransferCompletionMessage - invalid state")
    public void completeDataTransfer_invalidState() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        assertThrows(TransferProcessInvalidStateException.class,
                () -> service.completeDataTransfer(DataTransferMockObjectUtil.TRANSFER_COMPLETION_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR, auditEvent.getEventType());
    }

    // suspend
    @Test
    @DisplayName("TransferSuspensionMessage from STARTED - provider")
    public void suspendDataTransfer_fromStarted_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        TransferProcess transferProcessSuspended = service.suspendDataTransfer(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE,
                null, DataTransferMockObjectUtil.PROVIDER_PID);

        assertEquals(TransferState.SUSPENDED, transferProcessSuspended.getState());
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals(TransferState.SUSPENDED, argTransferProcess.getValue().getState());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED, auditEvent.getEventType());
    }

    @Test
    @DisplayName("TransferSuspensionMessage from STARTED - consumer callback")
    public void suspendDataTransfer_fromStarted_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED));

        TransferProcess transferProcessSuspended = service.suspendDataTransfer(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE,
                DataTransferMockObjectUtil.CONSUMER_PID, null);

        assertEquals(TransferState.SUSPENDED, transferProcessSuspended.getState());
        verify(transferProcessRepository).save(argTransferProcess.capture());
        assertEquals(TransferState.SUSPENDED, argTransferProcess.getValue().getState());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_SUSPENDED, auditEvent.getEventType());
    }

    @Test
    @DisplayName("TransferSuspensionMessage - transfer process not found - provider")
    public void suspendDataTransfer_tpNotFound_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.suspendDataTransfer(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, auditEvent.getEventType());
    }

    @Test
    @DisplayName("TransferSuspensionMessage - transfer process not found - consumer callback")
    public void suspendDataTransfer_tpNotFound_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.suspendDataTransfer(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE, DataTransferMockObjectUtil.CONSUMER_PID, null));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, auditEvent.getEventType());
    }

    @Test
    @DisplayName("TransferSuspensionMessage - invalid state")
    public void suspendDataTransfer_invalidState() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.of(DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER));

        assertThrows(TransferProcessInvalidStateException.class,
                () -> service.suspendDataTransfer(DataTransferMockObjectUtil.TRANSFER_SUSPENSION_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR, auditEvent.getEventType());
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

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_TERMINATED, auditEvent.getEventType());
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

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_TERMINATED, auditEvent.getEventType());
    }

    @Test
    @DisplayName("TransferTerminationMessage - transfer process not found - provider")
    public void terminateDataTransfer_tpNotFound_provider() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.terminateDataTransfer(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE, null, DataTransferMockObjectUtil.PROVIDER_PID));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, auditEvent.getEventType());
    }

    @Test
    @DisplayName("TransferTerminationMessage - transfer process not found - consumer callback")
    public void terminateDataTransfer_tpNotFound_consumer() {
        when(transferProcessRepository.findByConsumerPidAndProviderPid(any(String.class), any(String.class)))
                .thenReturn(Optional.empty());

        assertThrows(TransferProcessNotFoundException.class,
                () -> service.terminateDataTransfer(DataTransferMockObjectUtil.TRANSFER_TERMINATION_MESSAGE, DataTransferMockObjectUtil.CONSUMER_PID, null));
        verify(transferProcessRepository, times(0)).save(argTransferProcess.capture());

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_NOT_FOUND, auditEvent.getEventType());
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

        verify(publisher).publishEvent(argAuditEvent.capture());
        AuditEvent auditEvent = argAuditEvent.getValue();
        assertNotNull(auditEvent);
        assertEquals(AuditEventType.PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR, auditEvent.getEventType());
    }
}
