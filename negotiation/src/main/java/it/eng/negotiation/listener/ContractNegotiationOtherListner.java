package it.eng.negotiation.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import it.eng.negotiation.event.ContractNegotiationEvent;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ContractNegotiationOtherListner {

	@Async
	@EventListener
	void handleAsyncEvent(ContractNegotiationEvent event) {
		log.info("Handling other contract negotiation logic...");
	}
}
