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
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Encapsulates the retry scheduling and termination fallback for automatic negotiation.
 *
 * <h3>Retry semantics</h3>
 * <ul>
 *   <li>{@code retryCount} — persisted on {@link it.eng.negotiation.model.ContractNegotiation};
 *       counts the number of retries <em>already consumed</em> (0 on the first attempt).</li>
 *   <li>{@code maxRetries} — value of {@code application.automatic.negotiation.retry.max};
 *       the maximum number of <em>retries</em> permitted after the initial attempt.
 *       Total executions = maxRetries + 1.</li>
 *   <li>Setting {@code maxRetries = 0} disables retries: one attempt is made and a failure
 *       immediately triggers termination.</li>
 * </ul>
 *
 * <p>Retries are scheduled via {@link TaskScheduler} so that the event-dispatch thread is
 * released immediately after a failed attempt. No thread is held during the inter-retry
 * delay, preventing unbounded thread growth under persistent failure conditions.
 *
 * <p>Called exclusively from {@code AutomaticNegotiationListener}, which already runs
 * in a separate async thread via {@code AsynchronousSpringEventsConfig}.
 */
@Service
@Slf4j
public class AutomaticNegotiationService {

    private final ContractNegotiationAPIService apiService;
    private final ContractNegotiationProperties properties;
    private final ContractNegotiationRepository repository;
    private final AuditEventPublisher publisher;
    private final TaskScheduler taskScheduler;

    public AutomaticNegotiationService(ContractNegotiationAPIService apiService,
                                       ContractNegotiationProperties properties,
                                       ContractNegotiationRepository repository,
                                       AuditEventPublisher publisher,
                                       TaskScheduler taskScheduler) {
        this.apiService = apiService;
        this.properties = properties;
        this.repository = repository;
        this.publisher = publisher;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Provider: sends ContractAgreementMessage automatically.
     * Triggered after CN moves to REQUESTED (consumer-initiated) or ACCEPTED (offer-initiated).
     *
     * @param id the internal MongoDB id of the ContractNegotiation
     */
    public void processAgreed(String id) {
        scheduleAttempt(id, "AGREED", apiService::sendContractAgreementMessage);
    }

    /**
     * Provider: sends ContractNegotiationEventMessage:finalized automatically.
     * Triggered after CN moves to VERIFIED.
     *
     * @param id the internal MongoDB id of the ContractNegotiation
     */
    public void processFinalize(String id) {
        scheduleAttempt(id, "FINALIZE", apiService::sendContractNegotiationEventMessageFinalize);
    }

    /**
     * Consumer: sends ContractNegotiationEventMessage:accepted automatically.
     * Triggered after CN moves to OFFERED (initial offer only).
     *
     * @param id the internal MongoDB id of the ContractNegotiation
     */
    public void processAccepted(String id) {
        scheduleAttempt(id, "ACCEPTED", apiService::sendContractNegotiationEventMessageAccepted);
    }

    /**
     * Consumer: sends ContractAgreementVerificationMessage automatically.
     * Triggered after CN moves to AGREED.
     *
     * @param id the internal MongoDB id of the ContractNegotiation
     */
    public void processVerify(String id) {
        scheduleAttempt(id, "VERIFY", apiService::sendContractAgreementVerificationMessage);
    }

    /**
     * Executes a single attempt of the given action for the specified CN and phase.
     *
     * <p>On failure, increments and persists {@code retryCount}, then either schedules
     * the next attempt after the configured delay (if the retry budget has not been
     * exhausted) or transitions the CN to {@code TERMINATED}. The calling thread is
     * never blocked during the inter-retry delay.
     *
     * <p>Attempt numbers in log messages are 1-based: attempt 1 is the initial execution,
     * attempt 2 is the first retry, and so on up to {@code maxRetries + 1} total.
     *
     * @param id     the internal MongoDB id of the ContractNegotiation
     * @param phase  human-readable label used in log messages
     * @param action the protocol action to attempt, receiving the CN id
     */
    private void scheduleAttempt(String id, String phase, Consumer<String> action) {
        ContractNegotiation cn = repository.findById(id)
                .orElseThrow(() -> new ContractNegotiationNotFoundException("CN not found: " + id));

        int retryCount   = cn.getRetryCount();        // retries already consumed (0 on first attempt)
        int maxRetries   = properties.getMaxRetries(); // max retries allowed after the initial attempt
        int totalAttempts = maxRetries + 1;            // total executions: 1 initial + maxRetries retries
        int attemptNumber = retryCount + 1;            // 1-based: 1 = initial, 2 = first retry, …

        try {
            action.accept(id);
            log.info("Auto negotiation phase [{}] succeeded for CN {} (attempt {}/{})",
                    phase, id, attemptNumber, totalAttempts);
        } catch (Exception e) {
            int nextRetryCount = retryCount + 1;
            log.warn("Auto negotiation phase [{}] failed for CN {} (attempt {}/{}): {}",
                    phase, id, attemptNumber, totalAttempts, e.getMessage());

            repository.save(cn.withRetryCount(nextRetryCount));

            if (nextRetryCount > maxRetries) {
                log.error("Auto negotiation phase [{}] exhausted all {} attempt(s) for CN {}. Transitioning to TERMINATED.",
                        phase, totalAttempts, id);
                terminateGracefully(id);
            } else {
                long delayMs = properties.getRetryDelayMs();
                log.info("Auto negotiation phase [{}] scheduling retry {}/{} for CN {} in {} ms",
                        phase, nextRetryCount, maxRetries, id, delayMs);
                taskScheduler.schedule(
                        () -> scheduleAttempt(id, phase, action),
                        Instant.now().plusMillis(delayMs));
            }
        }
    }

    private void terminateGracefully(String id) {
        try {
            apiService.sendContractNegotiationTerminationMessage(id);
        } catch (Exception e) {
            log.error("Failed to send termination to peer for CN {}. Forcing local TERMINATED state. Reason: {}", id, e.getMessage());
            repository.findById(id).ifPresent(cn -> {
                var terminated = cn.withNewContractNegotiationState(ContractNegotiationState.TERMINATED);
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

