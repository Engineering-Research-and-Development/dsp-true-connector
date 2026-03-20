package it.eng.datatransfer.service;

import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

public class AutomaticDataTransferServiceTest {
    @Mock
    private DataTransferAPIService apiService;
    @Mock
    private TransferProcessRepository transferProcessRepository;
    @Mock
    private DataTransferProperties transferProperties;

    @InjectMocks
    private AutomaticDataTransferService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("processStart: succeeds on first attempt")
    void processStart_success() {
        String id = "tp1";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(3);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.startTransfer(id)).thenReturn(null);
        service.processStart(id);
        verify(apiService, times(1)).startTransfer(id);
        verify(transferProcessRepository, never()).save(any());
    }

    @Test
    @DisplayName("processStart: fails once, retries, then succeeds")
    void processStart_retryOnce_thenSuccess() {
        String id = "tp2";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(3);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.startTransfer(id))
            .thenThrow(new RuntimeException("fail"))
            .thenReturn(null);
        when(transferProcessRepository.save(any())).thenReturn(tp);
        service.processStart(id);
        verify(apiService, times(2)).startTransfer(id);
        verify(transferProcessRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("processStart: exhausts retries, calls terminateGracefully")
    void processStart_exhaustsRetries_terminationSucceeds() {
        String id = "tp3";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(2);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.startTransfer(id)).thenThrow(new RuntimeException("fail"));
        when(transferProcessRepository.save(any())).thenReturn(tp);
        when(apiService.terminateTransfer(id)).thenReturn(null);
        service.processStart(id);
        verify(apiService, times(3)).startTransfer(id);
        verify(apiService, times(1)).terminateTransfer(id);
    }

    @Test
    @DisplayName("processStart: exhausts retries, terminate also fails, forces local TERMINATED state")
    void processStart_exhaustsRetries_terminationAlsoFails() {
        String id = "tp4";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(1);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.startTransfer(id)).thenThrow(new RuntimeException("fail"));
        when(apiService.terminateTransfer(id)).thenThrow(new RuntimeException("failTerm"));
        when(transferProcessRepository.save(any())).thenReturn(tp);
        service.processStart(id);
        verify(apiService, times(2)).startTransfer(id);
        verify(apiService, times(1)).terminateTransfer(id);
        verify(transferProcessRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("processStart: interrupted during sleep")
    void processStart_interruptedDuringSleep() {
        String id = "tp5";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(2);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.startTransfer(id)).thenThrow(new RuntimeException("fail"));
        when(transferProcessRepository.save(any())).thenAnswer(invocation -> {
            Thread.currentThread().interrupt();
            return tp;
        });
        service.processStart(id);
        verify(apiService, atLeastOnce()).startTransfer(id);
    }

    @Test
    @DisplayName("processStart: resumes from persisted retryCount")
    void processStart_resumeFromPersistedRetryCount() {
        String id = "tp6";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_REQUESTED_PROVIDER.withRetryCount(2);
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(3);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.startTransfer(id)).thenReturn(null);
        service.processStart(id);
        verify(apiService, times(1)).startTransfer(id);
    }

    @Test
    @DisplayName("processDownload: succeeds on first attempt")
    void processDownload_success() {
        String id = "tp7";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(3);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.downloadData(id)).thenReturn(CompletableFuture.completedFuture(null));
        service.processDownload(id);
        verify(apiService, times(1)).downloadData(id);
        verify(transferProcessRepository, never()).save(any());
    }

    @Test
    @DisplayName("processDownload: fails once, retries, then succeeds")
    void processDownload_retryOnce_thenSuccess() {
        String id = "tp8";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(3);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.downloadData(id))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail")))
            .thenReturn(CompletableFuture.completedFuture(null));
        when(transferProcessRepository.save(any())).thenReturn(tp);
        service.processDownload(id);
        verify(apiService, times(2)).downloadData(id);
        verify(transferProcessRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("processDownload: exhausts retries, calls terminateGracefully")
    void processDownload_exhaustsRetries_terminationSucceeds() {
        String id = "tp9";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(2);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.downloadData(id)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail")));
        when(transferProcessRepository.save(any())).thenReturn(tp);
        when(apiService.terminateTransfer(id)).thenReturn(null);
        service.processDownload(id);
        verify(apiService, times(3)).downloadData(id);
        verify(apiService, times(1)).terminateTransfer(id);
    }

    @Test
    @DisplayName("processDownload: exhausts retries, terminate also fails, forces local TERMINATED state")
    void processDownload_exhaustsRetries_terminationAlsoFails() {
        String id = "tp10";
        var tp = DataTransferMockObjectUtil.TRANSFER_PROCESS_STARTED;
        when(transferProcessRepository.findById(id)).thenReturn(Optional.of(tp)).thenReturn(Optional.of(tp));
        when(transferProperties.getMaxRetryAttempts()).thenReturn(1);
        when(transferProperties.getRetryDelayMs()).thenReturn(1L);
        when(apiService.downloadData(id)).thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail")));
        when(apiService.terminateTransfer(id)).thenThrow(new RuntimeException("failTerm"));
        when(transferProcessRepository.save(any())).thenReturn(tp);
        service.processDownload(id);
        verify(apiService, times(2)).downloadData(id);
        verify(apiService, times(1)).terminateTransfer(id);
        verify(transferProcessRepository, atLeastOnce()).save(any());
    }

}
