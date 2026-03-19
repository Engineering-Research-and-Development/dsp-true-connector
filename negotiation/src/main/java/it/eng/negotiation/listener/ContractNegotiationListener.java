package it.eng.negotiation.listener;

import it.eng.negotiation.service.ContractNegotiationEventHandlerService;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ContractNegotiationListener {

	private ContractNegotiationEventHandlerService contractNegotiationEventHandlerService;

	public ContractNegotiationListener(ContractNegotiationEventHandlerService contractNegotiationEventHandlerService) {
		this.contractNegotiationEventHandlerService = contractNegotiationEventHandlerService;
	}


	@EventListener
	public void handleArtifactConsumedEvent(ArtifactConsumedEvent artifactConsumedEvent) {
		log.info("Handling ArtifactConsumedEvent...");
		contractNegotiationEventHandlerService.artifactConsumedEvent(artifactConsumedEvent);
	}
}
