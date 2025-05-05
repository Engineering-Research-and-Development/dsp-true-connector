package it.eng.negotiation.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import it.eng.negotiation.event.ContractNegotiationEvent;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.service.ContractNegotiationEventHandlerService;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponseEvent;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ContractNegotiationListener {
	
	private ContractNegotiationEventHandlerService contractNegotiationEventHandlerService;

	public ContractNegotiationListener(ContractNegotiationEventHandlerService contractNegotiationEventHandlerService) {
		this.contractNegotiationEventHandlerService = contractNegotiationEventHandlerService;
	}

	@EventListener
	void handleAsyncEvent(ContractNegotiationEvent event) {
		log.info("Handling other contract negotiation logic...");
	}
	
	@EventListener
	public void handleContractNegotiationOfferResponse(ContractNegotiationOfferResponseEvent response) {
		log.info("Handling ContractNegotiationOfferResponse...");
		contractNegotiationEventHandlerService.handleContractNegotiationOfferResponse(response);
	}
	
	@EventListener
	public void handleContractAgreementVerificationMessage(ContractAgreementVerificationMessage verificationMessage) {
		log.info("Handling ContractAgreementVerificationMessage...");
		contractNegotiationEventHandlerService.verifyNegotiation(verificationMessage.getConsumerPid(), verificationMessage.getProviderPid());
	}
	
	@EventListener
	public void handleArtifactConsumedEvent(ArtifactConsumedEvent artifactConsumedEvent) {
		log.info("Handling ArtifactConsumedEvent...");
		contractNegotiationEventHandlerService.artifactConsumedEvent(artifactConsumedEvent);
	}
}
