package it.eng.negotiation.listener;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ContractNegotiationPublisher {

	private final ApplicationEventPublisher publisher;

	ContractNegotiationPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}
	
	public void publishEvent(final Object event) {
		// Publishing an object as an event
		log.info("Publishing event - " + event.getClass().getSimpleName());
		publisher.publishEvent(event);
	}
}
