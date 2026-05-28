package it.eng.datatransfer.service;

import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferState;
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
}
