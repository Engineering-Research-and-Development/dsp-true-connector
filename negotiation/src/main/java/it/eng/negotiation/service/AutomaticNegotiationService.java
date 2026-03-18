package it.eng.negotiation.service;

import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.model.IConstants;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Encapsulates the retry loop and termination fallback for automatic negotiation.
 * Called exclusively from {@link AutomaticNegotiationListener}, which already runs
 * in a separate async thread via {@code AsynchronousSpringEventsConfig}.
 */
@Service
@Slf4j
public class AutomaticNegotiationService {

    private final ContractNegotiationAPIService apiService;
    private final ContractNegotiationProperties properties;
    private final ContractNegotiationRepository repository;
    private final AuditEventPublisher publisher;

    public AutomaticNegotiationService(ContractNegotiationAPIService apiService,
                                       ContractNegotiationProperties properties,
                                       ContractNegotiationRepository repository,
                                       AuditEventPublisher publisher) {
        this.apiService = apiService;
        this.properties = properties;
        this.repository = repository;
        this.publisher = publisher;
    }

    /**
     * Provider: sends ContractAgreementMessage automatically.
     * Triggered after CN moves to REQUESTED (consumer-initiated) or ACCEPTED (offer-initiated).
     *
     * @param id the internal MongoDB id of the ContractNegotiation
     */
    public void processAgreed(String id) {
        executeWithRetry(id, "AGREED", () -> apiService.sendContractAgreementMessage(id));
    }

    /**
     * Provider: sends ContractNegotiationEventMessage:finalized automatically.
     * Triggered after CN moves to VERIFIED.
     *
     * @param id the internal MongoDB id of the ContractNegotiation
     */
    public void processFinalize(String id) {
        executeWithRetry(id, "FINALIZE", () -> apiService.sendContractNegotiationEventMessageFinalize(id));
    }

    /**
     * Consumer: sends ContractNegotiationEventMessage:accepted automatically.
     * Triggered after CN moves to OFFERED (initial offer only).
     *
     * @param id the internal MongoDB id of the ContractNegotiation
     */
    public void processAccepted(String id) {
        executeWithRetry(id, "ACCEPTED", () -> apiService.sendContractNegotiationEventMessageAccepted(id));
    }

    /**
     * Consumer: sends ContractAgreementVerificationMessage automatically.
     * Triggered after CN moves to AGREED.
     *
     * @param id the internal MongoDB id of the ContractNegotiation
     */
    public void processVerify(String id) {
        executeWithRetry(id, "VERIFY", () -> apiService.sendContractAgreementVerificationMessage(id));
    }

    private void executeWithRetry(String id, String phase, Runnable action) {
        ContractNegotiation cn = repository.findById(id)
                .orElseThrow(() -> new ContractNegotiationNotFoundException("CN not found: " + id));

        // Resume from persisted retryCount so app restarts preserve retry budget
        int attempt = cn.getRetryCount();
        int maxRetries = properties.getMaxRetryAttempts();

        while (attempt <= maxRetries) {
            try {
                action.run();
                log.info("Auto negotiation phase [{}] succeeded for CN {} on attempt {}", phase, id, attempt);
                return;
            } catch (Exception e) {
                attempt++;
                log.warn("Auto negotiation phase [{}] failed for CN {}, attempt {}/{}: {}",
                        phase, id, attempt, maxRetries, e.getMessage());

                // Persist updated retryCount
                cn = cn.withRetryCount(attempt);
                repository.save(cn);

                if (attempt > maxRetries) {
                    log.error("Auto negotiation phase [{}] exhausted retries for CN {}. Transitioning to TERMINATED.", phase, id);
                    terminateGracefully(id);
                    return;
                }

                try {
                    Thread.sleep(properties.getRetryDelayMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Retry sleep interrupted for CN {}, phase [{}]. CN left in current state for manual recovery.", id, phase);
                    return;
                }
            }
        }
    }

    private void terminateGracefully(String id) {
        try {
            apiService.sendContractNegotiationTerminationMessage(id);
        } catch (Exception e) {
            log.error("Failed to send termination to peer for CN {}. Forcing local TERMINATED state. Reason: {}", id, e.getMessage());
            repository.findById(id).ifPresent(cn -> {
                ContractNegotiation terminated = cn.withNewContractNegotiationState(ContractNegotiationState.TERMINATED);
                repository.save(terminated);
                publisher.publishEvent(AuditEvent.Builder.newInstance()
                        .eventType(AuditEventType.PROTOCOL_NEGOTIATION_TERMINATED)
                        .description("CN force-terminated after retry exhaustion and failed termination message")
                        .details(Map.of("contractNegotiationId", id, "role", cn.getRole() != null ? cn.getRole() : IConstants.ROLE_PROVIDER))
                        .build());
            });
        }
    }
}

