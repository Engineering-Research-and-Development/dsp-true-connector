package it.eng.datatransfer.service;

import it.eng.datatransfer.exceptions.TransferProcessInvalidStateException;
import it.eng.datatransfer.model.*;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferArtifactStateRepository;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.model.IConstants;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.service.AuditEventPublisher;
import it.eng.tools.service.FieldEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractDataTransferServiceTest {

    private static final String CONSUMER_PID = "urn:uuid:consumer-abs-test";
    private static final String PROVIDER_PID = "urn:uuid:provider-abs-test";
    private static final String TP_ID = "tp-id-abs";

    @Mock private TransferProcessRepository transferProcessRepository;
    @Mock private AuditEventPublisher publisher;
    @Mock private OkHttpRestClient okHttpRestClient;
    @Mock private TransferRequestMessageRepository transferRequestMessageRepository;
    @Mock private DataTransferProperties dataTransferProperties;
    @Mock private TemporaryBucketUserService temporaryBucketUserService;
    @Mock private FieldEncryptionService fieldEncryptionService;
    @Mock private CancellationRegistry cancellationRegistry;
    @Mock private TransferArtifactStateRepository transferArtifactStateRepository;

    private AbstractDataTransferService service;

    @BeforeEach
    void setUp() {
        service = new DataTransferService(
                transferProcessRepository, transferRequestMessageRepository,
                publisher, okHttpRestClient, dataTransferProperties,
                temporaryBucketUserService, fieldEncryptionService,
                cancellationRegistry, transferArtifactStateRepository);
    }

    @Test
    @DisplayName("suspendDataTransfer records suspendedBy as the opposite of local role (the sender)")
    void suspendDataTransferRecordsSuspendedByAsSenderRole() {
        // Local role = CONSUMER → sender = PROVIDER
        TransferProcess tp = consumerPullTp(TransferState.STARTED);
        when(transferProcessRepository.findByConsumerPidAndProviderPid(CONSUMER_PID, PROVIDER_PID))
                .thenReturn(Optional.of(tp));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transferArtifactStateRepository.findById(TP_ID)).thenReturn(Optional.empty());
        when(transferArtifactStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransferSuspensionMessage msg = TransferSuspensionMessage.Builder.newInstance()
                .consumerPid(CONSUMER_PID).providerPid(PROVIDER_PID)
                .code("200").reason(List.of("test")).build();
        service.suspendDataTransfer(msg, CONSUMER_PID, PROVIDER_PID);

        verify(transferArtifactStateRepository).save(
                argThat(s -> IConstants.ROLE_PROVIDER.equals(s.getSuspendedBy())));
        verify(cancellationRegistry).signal(TP_ID);
    }

    @Test
    @DisplayName("startDataTransfer from SUSPENDED rejects resume when suspendedBy does not match sender")
    void startDataTransferRejectsMismatchedSuspendedBy() {
        // Local role = CONSUMER, suspendedBy = CONSUMER (consumer suspended it)
        // Sender = PROVIDER tries to resume → should be rejected
        TransferProcess suspended = consumerPullTp(TransferState.SUSPENDED);
        when(transferProcessRepository.findByConsumerPidAndProviderPid(CONSUMER_PID, PROVIDER_PID))
                .thenReturn(Optional.of(suspended));
        TransferArtifactState state = TransferArtifactState.Builder.newInstance()
                .id(TP_ID).suspendedBy(IConstants.ROLE_CONSUMER).build();
        when(transferArtifactStateRepository.findById(TP_ID)).thenReturn(Optional.of(state));

        TransferStartMessage msg = TransferStartMessage.Builder.newInstance()
                .consumerPid(CONSUMER_PID).providerPid(PROVIDER_PID).build();

        assertThrows(TransferProcessInvalidStateException.class,
                () -> service.startDataTransfer(msg, CONSUMER_PID, PROVIDER_PID));
    }

    @Test
    @DisplayName("startDataTransfer from SUSPENDED succeeds when suspendedBy matches sender")
    void startDataTransferSucceedsWhenSuspendedByMatchesSender() {
        // Local role = CONSUMER, suspendedBy = PROVIDER, sender = PROVIDER → allowed
        TransferProcess suspended = consumerPullTp(TransferState.SUSPENDED);
        when(transferProcessRepository.findByConsumerPidAndProviderPid(CONSUMER_PID, PROVIDER_PID))
                .thenReturn(Optional.of(suspended));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        TransferArtifactState state = TransferArtifactState.Builder.newInstance()
                .id(TP_ID).suspendedBy(IConstants.ROLE_PROVIDER).build();
        when(transferArtifactStateRepository.findById(TP_ID)).thenReturn(Optional.of(state));
        when(transferArtifactStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransferStartMessage msg = TransferStartMessage.Builder.newInstance()
                .consumerPid(CONSUMER_PID).providerPid(PROVIDER_PID).build();

        assertDoesNotThrow(() -> service.startDataTransfer(msg, CONSUMER_PID, PROVIDER_PID));
    }

    @Test
    @DisplayName("startDataTransfer for CONSUMER+HTTP_PULL publishes AutoTransferDownloadEvent without automaticTransfer guard")
    void startDataTransferAlwaysPublishesAutoDownloadForConsumerPull() {
        TransferProcess requested = TransferProcess.Builder.newInstance()
                .id(TP_ID).consumerPid(CONSUMER_PID).providerPid(PROVIDER_PID)
                .state(TransferState.REQUESTED).role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format()).build();
        when(transferProcessRepository.findByConsumerPidAndProviderPid(CONSUMER_PID, PROVIDER_PID))
                .thenReturn(Optional.of(requested));
        when(transferProcessRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransferStartMessage msg = TransferStartMessage.Builder.newInstance()
                .consumerPid(CONSUMER_PID).providerPid(PROVIDER_PID).build();

        service.startDataTransfer(msg, CONSUMER_PID, PROVIDER_PID);

        verify(publisher).publishEvent(any(it.eng.datatransfer.event.AutoTransferDownloadEvent.class));
    }

    // Helper
    private TransferProcess consumerPullTp(TransferState state) {
        return TransferProcess.Builder.newInstance()
                .id(TP_ID).consumerPid(CONSUMER_PID).providerPid(PROVIDER_PID)
                .state(state).role(IConstants.ROLE_CONSUMER)
                .format(DataTransferFormat.HTTP_PULL.format()).build();
    }
}