package it.eng.negotiation.listener;

import it.eng.negotiation.event.ContractNegotiationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ContractNegotiationOtherListner {

    @EventListener
    void handleAsyncEvent(ContractNegotiationEvent event) {
        log.info("Handling other contract negotiation logic...");
    }
}
