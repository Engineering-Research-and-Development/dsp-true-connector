package it.eng.datatransfer.service;

import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.service.AuditEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AbstractDataTransferServiceTest {

    @Mock
    private TransferProcessRepository transferProcessRepository;
    @Mock
    private TransferRequestMessageRepository transferRequestMessageRepository;
    @Mock
    private AuditEventPublisher publisher;
    @Mock
    private DataTransferProperties transferProperties;
    @Mock
    private TemporaryBucketUserService temporaryBucketUserService;

    @InjectMocks
    private DataTransferService service;

    @Test
    @DisplayName("startDataTransfer accepts resume message from peer who initiated the suspension")
    public void startDataTransfer_acceptsResumeOnlyFromPeerInitiator() {
        // SUSPENDED_CONSUMER: role=PROVIDER, suspendedBy=CONSUMER
        // The consumer (peer) initiated the suspension → consumer may send the resume → allow
        TransferProcess suspended = DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_CONSUMER;

        TransferStartMessage message = TransferStartMessage.Builder.newInstance()
                .consumerPid(suspended.getConsumerPid())
                .providerPid(suspended.getProviderPid())
                .build();

        when(transferProcessRepository.findByConsumerPidAndProviderPid(
                suspended.getConsumerPid(), suspended.getProviderPid()))
                .thenReturn(Optional.of(suspended));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransferProcess started = service.startDataTransfer(message, suspended.getConsumerPid(), null);

        assertEquals(TransferState.STARTED, started.getState());
        assertEquals("SUSPENDED", started.getDataFlowState());
        assertNull(started.getSuspendedBy());
    }

    @Test
    @DisplayName("startDataTransfer rejects resume when the local side was the one who suspended")
    public void startDataTransfer_rejectsPeerResumeWhenLocalInitiatedSuspension() {
        // SUSPENDED_PROVIDER: role=PROVIDER, suspendedBy=PROVIDER
        // The provider (local side) initiated the suspension → peer (consumer) cannot resume via protocol
        TransferProcess suspended = DataTransferMockObjectUtil.TRANSFER_PROCESS_SUSPENDED_PROVIDER;

        TransferStartMessage message = TransferStartMessage.Builder.newInstance()
                .consumerPid(suspended.getConsumerPid())
                .providerPid(suspended.getProviderPid())
                .build();

        when(transferProcessRepository.findByConsumerPidAndProviderPid(
                suspended.getConsumerPid(), suspended.getProviderPid()))
                .thenReturn(Optional.of(suspended));

        assertThrows(TransferProcessInvalidStateException.class,
                () -> service.startDataTransfer(message, suspended.getConsumerPid(), null));

        verify(transferProcessRepository, times(0)).save(any(TransferProcess.class));
    }

    @Test
    @DisplayName("suspendDataTransfer persists mirrored remote initiator role as suspendedBy")
    public void suspendDataTransfer_persistsMirroredRemoteInitiatorRole() {
        // Test scenario: local role=PROVIDER → suspendedBy should be CONSUMER (opposite side)
        TransferProcess started = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        // TRANSFER_PROCESS_STARTED has role=ROLE_PROVIDER
        assertEquals("provider", started.getRole());

        when(transferProcessRepository.findByConsumerPidAndProviderPid(
                started.getConsumerPid(), started.getProviderPid()))
                .thenReturn(Optional.of(started));
        when(transferProcessRepository.save(any(TransferProcess.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransferSuspensionMessage suspensionMessage = TransferSuspensionMessage.Builder.newInstance()
                .consumerPid(started.getConsumerPid())
                .providerPid(started.getProviderPid())
                .code("123")
                .build();

        TransferProcess suspended = service.suspendDataTransfer(
                suspensionMessage, started.getConsumerPid(), started.getProviderPid());

        assertEquals(TransferState.SUSPENDED, suspended.getState());
        // When local role is PROVIDER, suspendedBy must be the opposite (CONSUMER)
        assertEquals("consumer", suspended.getSuspendedBy());

        verify(transferProcessRepository).save(argThat(tp ->
                TransferState.SUSPENDED.equals(tp.getState())
                        && "consumer".equals(tp.getSuspendedBy())));
    }
}
