package it.eng.negotiation.listener;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import it.eng.negotiation.event.ContractNegotiationEvent;

@Component
public class ContractNegotiationPublisher {

	private final ApplicationEventPublisher publisher;

	ContractNegotiationPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	public void publishEvent(final ContractNegotiationEvent event) {
		// Publishing an object as an event
		publisher.publishEvent(event);
	}
}
