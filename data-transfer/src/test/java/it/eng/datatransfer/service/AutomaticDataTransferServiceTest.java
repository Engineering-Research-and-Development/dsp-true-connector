package it.eng.datatransfer.service;

import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AutomaticDataTransferServiceTest {

    @Mock
    private DataTransferAPIService apiService;
    @Mock
    private TransferProcessRepository transferProcessRepository;
    @Mock
    private DataTransferProperties transferProperties;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private AuditEventPublisher auditEventPublisher;

    private AutomaticDataTransferService service;

    void setUp() {
        service = new AutomaticDataTransferService(apiService, transferProcessRepository, transferProperties, taskScheduler, auditEventPublisher);
    }

    /**
     * Captures the Runnable most recently scheduled on taskScheduler and runs it
     * immediately, simulating the retry without any real delay.
     * Resets the scheduler mock before capturing so each call is independent of
     * previous invocations.
     */
    private void runScheduledRetry() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(captor.capture(), any(Instant.class));
        Runnable retry = captor.getValue();
        clearInvocations(taskScheduler);
        retry.run();
    }

    @Test
    @DisplayName("processStart: succeeds on first attempt")
    void processStart_success() {
        setUp();
        String id = "tp1";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(3);
        when(apiService.startTransfer(id)).thenReturn(null);

        service.processStart(id);

        verify(apiService, times(1)).startTransfer(id);
        verify(transferProcessRepository, never()).save(any());
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        verify(auditEventPublisher, never()).publishEvent(any(AuditEventType.class), anyString(), anyMap());
    }

    @Test
    @DisplayName("processStart: not found returns early")
    void processStart_notFound() {
        setUp();
        String id = "tp_missing";
        when(transferProcessRepository.findById(id)).thenReturn(Optional.empty());

        service.processStart(id);

        verify(apiService, never()).startTransfer(id);
        verify(transferProcessRepository, never()).save(any());
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("processStart: fails once, schedules retry with TaskScheduler")
    void processStart_failsOnce_schedulesRetry() {
        setUp();
        String id = "tp2";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(3);
        when(transferProperties.getRetryDelayMs()).thenReturn(1000L);
        when(apiService.startTransfer(id)).thenThrow(new RuntimeException("fail"));
        when(transferProcessRepository.save(any())).thenReturn(tp);

        service.processStart(id);

        verify(apiService, times(1)).startTransfer(id);
        verify(transferProcessRepository, times(1)).save(argThat(t -> t.getRetryCount() == 1));
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
        verify(auditEventPublisher, never()).publishEvent(any(AuditEventType.class), anyString(), anyMap());
    }

    @Test
    @DisplayName("processStart: exhausts retries, calls terminateTransfer successfully")
    void processStart_exhaustsRetries_terminationSucceeds() {
        setUp();
        String id = "tp3";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER;
        var saved = new AtomicReference<>(tp);
        
        when(transferProcessRepository.findById(id)).thenAnswer(inv -> Optional.of(saved.get()));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(2);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.startTransfer(id)).thenThrow(new RuntimeException("fail"));
        when(transferProcessRepository.save(any())).thenAnswer(inv -> {
            var cn = inv.<it.eng.datatransfer.model.TransferProcess>getArgument(0);
            saved.set(cn);
            return cn;
        });
        when(apiService.terminateTransfer(id)).thenReturn(null);

        // Attempt 0 fails → retryCount=1, retry scheduled
        service.processStart(id);
        // Attempt 1 fails → retryCount=2, retry scheduled
        runScheduledRetry();
        // Attempt 2 fails → retryCount=3 > maxRetries=2 → terminateGracefully
        runScheduledRetry();

        verify(apiService, times(3)).startTransfer(id);
        verify(transferProcessRepository, times(3)).save(any());
        verify(apiService, times(1)).terminateTransfer(id);
    }

    @Test
    @DisplayName("processStart: exhausts retries, terminate fails, publishes audit event for local TERMINATED")
    void processStart_exhaustsRetries_terminationFails_publishesAuditEvent() {
        setUp();
        String id = "tp4";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER;
        var saved = new AtomicReference<>(tp);
        
        when(transferProcessRepository.findById(id)).thenAnswer(inv -> Optional.of(saved.get()));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(1);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.startTransfer(id)).thenThrow(new RuntimeException("fail"));
        when(transferProcessRepository.save(any())).thenAnswer(inv -> {
            var cn = inv.<it.eng.datatransfer.model.TransferProcess>getArgument(0);
            saved.set(cn);
            return cn;
        });
        when(apiService.terminateTransfer(id)).thenThrow(new RuntimeException("failTerm"));

        // Attempt 0 fails → retryCount=1, retry scheduled
        service.processStart(id);
        // Attempt 1 fails → retryCount=2 > maxRetries=1 → terminateGracefully
        runScheduledRetry();

        verify(apiService, times(2)).startTransfer(id);
        verify(apiService, times(1)).terminateTransfer(id);
        verify(transferProcessRepository, times(3)).save(any());
        
        // Verify audit event was published for forced local termination
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditEventPublisher).publishEvent(
                eq(AuditEventType.PROTOCOL_TRANSFER_TERMINATED),
                argThat(desc -> desc.contains("forcibly terminated")),
                detailsCaptor.capture());
        
        Map<String, Object> details = detailsCaptor.getValue();
        assertTrue(details.containsKey("transferProcess"));
        assertEquals("Automatic retry exhaustion", details.get("reason"));
    }

    @Test
    @DisplayName("processStart: resumes from persisted retryCount")
    void processStart_resumeFromPersistedRetryCount() {
        setUp();
        String id = "tp6";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.withRetryCount(2);
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(3);
        when(apiService.startTransfer(id)).thenReturn(null);

        service.processStart(id);

        verify(apiService, times(1)).startTransfer(id);
        verify(transferProcessRepository, never()).save(any());
    }

    @Test
    @DisplayName("processDownload: succeeds on first attempt")
    void processDownload_success() {
        setUp();
        String id = "tp7";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(3);
        when(apiService.downloadData(id)).thenReturn(CompletableFuture.completedFuture(null));

        service.processDownload(id);

        verify(apiService, times(1)).downloadData(id);
        verify(transferProcessRepository, never()).save(any());
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        verify(auditEventPublisher, never()).publishEvent(any(AuditEventType.class), anyString(), anyMap());
    }

    @Test
    @DisplayName("processDownload: not found returns early")
    void processDownload_notFound() {
        setUp();
        String id = "tp_missing";
        when(transferProcessRepository.findById(id)).thenReturn(Optional.empty());

        service.processDownload(id);

        verify(apiService, never()).downloadData(id);
        verify(transferProcessRepository, never()).save(any());
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    @DisplayName("processDownload: fails once, schedules retry with TaskScheduler")
    void processDownload_failsOnce_schedulesRetry() {
        setUp();
        String id = "tp8";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(3);
        when(transferProperties.getRetryDelayMs()).thenReturn(1000L);
        when(apiService.downloadData(id)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail")));
        when(transferProcessRepository.save(any())).thenReturn(tp);

        service.processDownload(id);

        verify(apiService, times(1)).downloadData(id);
        verify(transferProcessRepository, times(1)).save(argThat(t -> t.getRetryCount() == 1));
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
        verify(auditEventPublisher, never()).publishEvent(any(AuditEventType.class), anyString(), anyMap());
    }

    @Test
    @DisplayName("processDownload: exhausts retries, calls terminateTransfer successfully")
    void processDownload_exhaustsRetries_terminationSucceeds() {
        setUp();
        String id = "tp9";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        var saved = new AtomicReference<>(tp);
        
        when(transferProcessRepository.findById(id)).thenAnswer(inv -> Optional.of(saved.get()));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(2);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.downloadData(id)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail")));
        when(transferProcessRepository.save(any())).thenAnswer(inv -> {
            var cn = inv.<it.eng.datatransfer.model.TransferProcess>getArgument(0);
            saved.set(cn);
            return cn;
        });
        when(apiService.terminateTransfer(id)).thenReturn(null);

        // Attempt 0 fails → retryCount=1, retry scheduled
        service.processDownload(id);
        // Attempt 1 fails → retryCount=2, retry scheduled
        runScheduledRetry();
        // Attempt 2 fails → retryCount=3 > maxRetries=2 → terminateGracefully
        runScheduledRetry();

        verify(apiService, times(3)).downloadData(id);
        verify(transferProcessRepository, times(3)).save(any());
        verify(apiService, times(1)).terminateTransfer(id);
    }

    @Test
    @DisplayName("processDownload: exhausts retries, terminate fails, publishes audit event for local TERMINATED")
    void processDownload_exhaustsRetries_terminationFails_publishesAuditEvent() {
        setUp();
        String id = "tp10";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        var saved = new AtomicReference<>(tp);
        
        when(transferProcessRepository.findById(id)).thenAnswer(inv -> Optional.of(saved.get()));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(1);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.downloadData(id)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail")));
        when(transferProcessRepository.save(any())).thenAnswer(inv -> {
            var cn = inv.<it.eng.datatransfer.model.TransferProcess>getArgument(0);
            saved.set(cn);
            return cn;
        });
        when(apiService.terminateTransfer(id)).thenThrow(new RuntimeException("failTerm"));

        // Attempt 0 fails → retryCount=1, retry scheduled
        service.processDownload(id);
        // Attempt 1 fails → retryCount=2 > maxRetries=1 → terminateGracefully
        runScheduledRetry();

        verify(apiService, times(2)).downloadData(id);
        verify(apiService, times(1)).terminateTransfer(id);
        verify(transferProcessRepository, times(3)).save(any());
        
        // Verify audit event was published for forced local termination
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditEventPublisher).publishEvent(
                eq(AuditEventType.PROTOCOL_TRANSFER_TERMINATED),
                argThat(desc -> desc.contains("forcibly terminated")),
                detailsCaptor.capture());
        
        Map<String, Object> details = detailsCaptor.getValue();
        assertTrue(details.containsKey("transferProcess"));
        assertEquals("Automatic retry exhaustion", details.get("reason"));
    }

}

