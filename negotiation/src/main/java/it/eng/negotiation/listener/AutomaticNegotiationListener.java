package it.eng.negotiation.listener;

import it.eng.negotiation.event.AutoNegotiationAcceptedEvent;
import it.eng.negotiation.event.AutoNegotiationAgreedEvent;
import it.eng.negotiation.event.AutoNegotiationFinalizeEvent;
import it.eng.negotiation.event.AutoNegotiationVerifyEvent;
import it.eng.negotiation.service.AutomaticNegotiationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens for automatic negotiation domain events and delegates to {@link AutomaticNegotiationService}.
 * All handlers are dispatched asynchronously by {@code AsynchronousSpringEventsConfig} —
 * no {@code @Async} annotation needed.
 */
@Component
@Slf4j
public class AutomaticNegotiationListener {

    private final AutomaticNegotiationService automaticNegotiationService;

    public AutomaticNegotiationListener(AutomaticNegotiationService automaticNegotiationService) {
        this.automaticNegotiationService = automaticNegotiationService;
    }

    /**
     * Handles automatic agreement: Provider sends ContractAgreementMessage.
     * Triggered when CN moves to REQUESTED (consumer-initiated) or ACCEPTED (offer-initiated).
     *
     * @param event the event carrying the CN internal id
     */
    @EventListener
    public void handleAutoNegotiationAgreed(AutoNegotiationAgreedEvent event) {
        log.info("PROVIDER - Auto: AutoNegotiationAgreedEvent for CN {}", event.contractNegotiationId());
        automaticNegotiationService.processAgreed(event.contractNegotiationId());
    }

    /**
     * Handles automatic finalization: Provider sends ContractNegotiationEventMessage:finalized.
     * Triggered when CN moves to VERIFIED.
     *
     * @param event the event carrying the CN internal id
     */
    @EventListener
    public void handleAutoNegotiationFinalize(AutoNegotiationFinalizeEvent event) {
        log.info("PROVIDER - Auto: AutoNegotiationFinalizeEvent for CN {}", event.contractNegotiationId());
        automaticNegotiationService.processFinalize(event.contractNegotiationId());
    }

    /**
     * Handles automatic acceptance: Consumer sends ContractNegotiationEventMessage:accepted.
     * Triggered when CN moves to OFFERED (initial offer only).
     *
     * @param event the event carrying the CN internal id
     */
    @EventListener
    public void handleAutoNegotiationAccepted(AutoNegotiationAcceptedEvent event) {
        log.info("CONSUMER - Auto: AutoNegotiationAcceptedEvent for CN {}", event.contractNegotiationId());
        automaticNegotiationService.processAccepted(event.contractNegotiationId());
    }

    /**
     * Handles automatic verification: Consumer sends ContractAgreementVerificationMessage.
     * Triggered when CN moves to AGREED.
     *
     * @param event the event carrying the CN internal id
     */
    @EventListener
    public void handleAutoNegotiationVerify(AutoNegotiationVerifyEvent event) {
        log.info("CONSUMER - Auto: AutoNegotiationVerifyEvent for CN {}", event.contractNegotiationId());
        automaticNegotiationService.processVerify(event.contractNegotiationId());
    }
}

