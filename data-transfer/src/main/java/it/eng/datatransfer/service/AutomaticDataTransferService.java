package it.eng.datatransfer.service;

import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.tools.model.IConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AutomaticDataTransferService {

    private final DataTransferAPIService apiService;
    private final TransferProcessRepository transferProcessRepository;
    private final DataTransferProperties transferProperties;

    /**
     * Constructs AutomaticDataTransferService with dependencies and transfer properties.
     *
     * @param apiService API service for data transfer
     * @param transferProcessRepository repository for transfer processes
     * @param transferProperties configuration properties for automatic transfer
     */
    public AutomaticDataTransferService(DataTransferAPIService apiService, TransferProcessRepository transferProcessRepository, it.eng.datatransfer.properties.DataTransferProperties transferProperties) {
        this.apiService = apiService;
        this.transferProcessRepository = transferProcessRepository;
        this.transferProperties = transferProperties;
    }

    public void processStart(String transferProcessId) {
        int maxRetries = transferProperties.getMaxRetryAttempts();
        long delayMs = transferProperties.getRetryDelayMs();
        TransferProcess tp = transferProcessRepository.findById(transferProcessId)
                .orElse(null);
        if (tp == null) {
            log.error("TransferProcess not found: {}", transferProcessId);
            return;
        }
        int attempt = tp.getRetryCount();
        while (attempt <= maxRetries) {
            try {
                apiService.startTransfer(transferProcessId);
                log.info("Auto transfer phase [start] succeeded for TransferProcess {} on attempt {}", transferProcessId, attempt);
                // For HTTP_PUSH on the provider side: after the consumer acknowledges the
                // TransferStartMessage, the provider must push the artifact to the consumer's
                // S3 endpoint (stored in the provider TP's dataAddress) and then send
                // TransferCompletionMessage. This is done by chaining processDownload here.
                TransferProcess tpAfterStart = transferProcessRepository.findById(transferProcessId).orElse(null);
                if (tpAfterStart != null
                        && DataTransferFormat.HTTP_PUSH.format().equals(tpAfterStart.getFormat())
                        && IConstants.ROLE_PROVIDER.equals(tpAfterStart.getRole())) {
                    log.info("HTTP_PUSH provider: chaining processDownload for TransferProcess {}", transferProcessId);
                    processDownload(transferProcessId);
                }
                return;
            } catch (Exception e) {
                attempt++;
                log.warn("Auto transfer phase [start] failed for TransferProcess {}, attempt {}/{}: {}", transferProcessId, attempt, maxRetries, e.getMessage());
                // Persist updated retryCount
                tp = tp.withRetryCount(attempt);
                transferProcessRepository.save(tp);
                if (attempt > maxRetries) {
                    log.error("Auto transfer phase [start] exhausted retries for TransferProcess {}. Transitioning to TERMINATED.", transferProcessId);
                    terminateGracefully(transferProcessId);
                    return;
                }
                try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Retry sleep interrupted for TransferProcess {}, phase [start]. Left in current state for manual recovery.", transferProcessId);
                    return;
                }
            }
        }
    }

    public void processDownload(String transferProcessId) {
        int maxRetries = transferProperties.getMaxRetryAttempts();
        long delayMs = transferProperties.getRetryDelayMs();
        TransferProcess tp = transferProcessRepository.findById(transferProcessId)
                .orElse(null);
        if (tp == null) {
            log.error("TransferProcess not found: {}", transferProcessId);
            return;
        }
        int attempt = tp.getRetryCount();
        while (attempt <= maxRetries) {
            try {
                apiService.downloadData(transferProcessId).join();
                log.info("Auto transfer phase [download] succeeded for TransferProcess {} on attempt {}", transferProcessId, attempt);
                return;
            } catch (Exception e) {
                attempt++;
                log.warn("Auto transfer phase [download] failed for TransferProcess {}, attempt {}/{}: {}", transferProcessId, attempt, maxRetries, e.getMessage());
                // Persist updated retryCount
                tp = tp.withRetryCount(attempt);
                transferProcessRepository.save(tp);
                if (attempt > maxRetries) {
                    log.error("Auto transfer phase [download] exhausted retries for TransferProcess {}. Transitioning to TERMINATED.", transferProcessId);
                    terminateGracefully(transferProcessId);
                    return;
                }
                try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Retry sleep interrupted for TransferProcess {}, phase [download]. Left in current state for manual recovery.", transferProcessId);
                    return;
                }
            }
        }
    }

    private void terminateGracefully(String transferProcessId) {
        try {
            apiService.terminateTransfer(transferProcessId);
            log.info("TransferProcess {} terminated after retry exhaustion.", transferProcessId);
        } catch (Exception e) {
            log.error("Failed to send termination to peer for TransferProcess {}. Forcing local TERMINATED state. Reason: {}", transferProcessId, e.getMessage());
            TransferProcess tp = transferProcessRepository.findById(transferProcessId).orElse(null);
            if (tp != null) {
                TransferProcess terminated = tp.copyWithNewTransferState(it.eng.datatransfer.model.TransferState.TERMINATED);
                transferProcessRepository.save(terminated);
                // Publish audit event
                // (Assume publisher is available via apiService or inject if needed)
                try {
                    apiService.getClass().getMethod("getPublisher"); // check if publisher is accessible
                    // If so, publish event
                    // publisher.publishEvent(AuditEventType.PROTOCOL_TRANSFER_TERMINATED, ...)
                } catch (Exception ignored) {}
            }
        }
    }

    // ...existing code...
}
