package it.eng.datatransfer.service;

import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.properties.DataTransferProperties;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.service.api.DataTransferAPIService;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.IConstants;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

@Service
@Slf4j
public class AutomaticDataTransferService {

    private final DataTransferAPIService apiService;
    private final TransferProcessRepository transferProcessRepository;
    private final DataTransferProperties transferProperties;
    private final TaskScheduler taskScheduler;
    private final AuditEventPublisher auditEventPublisher;

    /**
     * Constructs AutomaticDataTransferService with dependencies and transfer properties.
     *
     * @param apiService API service for data transfer
     * @param transferProcessRepository repository for transfer processes
     * @param transferProperties configuration properties for automatic transfer
     * @param taskScheduler scheduler for non-blocking retry scheduling
     * @param auditEventPublisher publisher for audit events
     */
    public AutomaticDataTransferService(DataTransferAPIService apiService, TransferProcessRepository transferProcessRepository,
                                        DataTransferProperties transferProperties, TaskScheduler taskScheduler,
                                        AuditEventPublisher auditEventPublisher) {
        this.apiService = apiService;
        this.transferProcessRepository = transferProcessRepository;
        this.transferProperties = transferProperties;
        this.taskScheduler = taskScheduler;
        this.auditEventPublisher = auditEventPublisher;
    }

    public void processStart(String transferProcessId) {
        scheduleAttempt(transferProcessId, "START", id -> {
            apiService.startTransfer(id);
            // For HTTP_PUSH on the provider side: after the consumer acknowledges the
            // TransferStartMessage, the provider must push the artifact to the consumer's
            // S3 endpoint (stored in the provider TP's dataAddress) and then send
            // TransferCompletionMessage. This is done by chaining processDownload here.
            TransferProcess tpAfterStart = transferProcessRepository.findById(id).orElse(null);
            if (tpAfterStart != null
                    && DataTransferFormat.HTTP_PUSH.format().equals(tpAfterStart.getFormat())
                    && IConstants.ROLE_PROVIDER.equals(tpAfterStart.getRole())) {
                log.info("HTTP_PUSH provider: chaining processDownload for TransferProcess {}", id);
                processDownload(id);
            }
        });
    }

    public void processDownload(String transferProcessId) {
        scheduleAttempt(transferProcessId, "DOWNLOAD", id -> apiService.downloadData(id).join());
    }

    /**
     * Executes a single attempt of the given action for the specified TransferProcess and phase.
     *
     * <p>On failure, increments and persists {@code retryCount}, then either schedules
     * the next attempt after the configured delay (if the retry budget has not been
     * exhausted) or transitions the TP to {@code TERMINATED}. The calling thread is
     * never blocked during the inter-retry delay.
     *
     * <p>Attempt numbers in log messages are 1-based: attempt 1 is the initial execution,
     * attempt 2 is the first retry, and so on up to {@code maxRetries + 1} total.
     *
     * @param id     the internal MongoDB id of the TransferProcess
     * @param phase  human-readable label used in log messages
     * @param action the transfer action to attempt, receiving the TP id
     */
    private void scheduleAttempt(String id, String phase, Consumer<String> action) {
        TransferProcess tp = transferProcessRepository.findById(id)
                .orElse(null);
        if (tp == null) {
            log.error("TransferProcess not found: {}", id);
            return;
        }

        int retryCount    = tp.getRetryCount();              // retries already consumed (0 on first attempt)
        int maxRetries    = transferProperties.getMaxRetryAttempts(); // max retries allowed after the initial attempt
        int totalAttempts = maxRetries + 1;                 // total executions: 1 initial + maxRetries retries
        int attemptNumber = retryCount + 1;                 // 1-based: 1 = initial, 2 = first retry, …

        try {
            action.accept(id);
            log.info("Auto transfer phase [{}] succeeded for TransferProcess {} (attempt {}/{})",
                    phase, id, attemptNumber, totalAttempts);
        } catch (Exception e) {
            int nextRetryCount = retryCount + 1;
            log.warn("Auto transfer phase [{}] failed for TransferProcess {} (attempt {}/{}): {}",
                    phase, id, attemptNumber, totalAttempts, e.getMessage());

            transferProcessRepository.save(tp.withRetryCount(nextRetryCount));

            if (nextRetryCount > maxRetries) {
                log.error("Auto transfer phase [{}] exhausted all {} attempt(s) for TransferProcess {}. Transitioning to TERMINATED.",
                        phase, totalAttempts, id);
                terminateGracefully(id);
            } else {
                long delayMs = transferProperties.getRetryDelayMs();
                log.info("Auto transfer phase [{}] scheduling retry {}/{} for TransferProcess {} in {} ms",
                        phase, nextRetryCount, maxRetries, id, delayMs);
                taskScheduler.schedule(
                        () -> scheduleAttempt(id, phase, action),
                        Instant.now().plusMillis(delayMs));
            }
        }
    }

    /**
     * Terminates a TransferProcess gracefully after retry exhaustion.
     *
     * <p>Attempts to send a termination message to the peer. If that succeeds, the process
     * is already transitioned to TERMINATED by the peer's response. If the peer communication
     * fails, this method forces a local TERMINATED state and publishes an audit event.
     *
     * @param transferProcessId the internal MongoDB id of the TransferProcess
     */
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
                
                // Publish audit event for local forced termination
                auditEventPublisher.publishEvent(
                        AuditEventType.PROTOCOL_TRANSFER_TERMINATED,
                        "Transfer process forcibly terminated after automatic retry exhaustion",
                        Map.of(
                                "transferProcess", terminated,
                                "role", IConstants.ROLE_API,
                                "consumerPid", terminated.getConsumerPid(),
                                "providerPid", terminated.getProviderPid(),
                                "reason", "Automatic retry exhaustion"));
            }
        }
    }
}
