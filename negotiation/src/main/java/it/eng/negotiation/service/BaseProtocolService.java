package it.eng.negotiation.service;

import it.eng.negotiation.exception.ContractNegotiationExistsException;
import it.eng.negotiation.exception.ContractNegotiationInvalidStateException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.tools.client.rest.OkHttpRestClient;

public abstract class BaseProtocolService {
	
	protected final ContractNegotiationPublisher publisher;
	protected final ContractNegotiationRepository contractNegotiationRepository;
	protected final OkHttpRestClient okHttpRestClient;
	protected final ContractNegotiationProperties properties;
	protected final OfferRepository offerRepository;

    public BaseProtocolService(ContractNegotiationPublisher publisher,
			ContractNegotiationRepository contractNegotiationRepository, OkHttpRestClient okHttpRestClient,
			ContractNegotiationProperties properties, OfferRepository offerRepository) {
		super();
		this.publisher = publisher;
		this.contractNegotiationRepository = contractNegotiationRepository;
		this.okHttpRestClient = okHttpRestClient;
		this.properties = properties;
		this.offerRepository = offerRepository;
	}

	protected ContractNegotiation findContractNegotiationByPids (String consumerPid, String providerPid) {
		return contractNegotiationRepository.findByProviderPidAndConsumerPid(providerPid, consumerPid)
				.orElseThrow(() -> new ContractNegotiationNotFoundException(
						"Contract negotiation with providerPid " + providerPid + 
						" and consumerPid " + consumerPid + " not found", consumerPid, providerPid));
	}
    
    protected void stateTransitionCheck (ContractNegotiationState newState, ContractNegotiation contractNegotiation) {
		if (!contractNegotiation.getState().canTransitTo(newState)) {
			throw new ContractNegotiationInvalidStateException("State transition aborted, " + contractNegotiation.getState().name()
					+ " state can not transition to " + newState.name(),
					contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
		}
	}
    
    protected void checkIfContractNegotiationExists (String consumerPid, String providerPid) {
    	contractNegotiationRepository
		.findByProviderPidAndConsumerPid(providerPid, consumerPid)
		.ifPresent(cn -> {
				throw new ContractNegotiationExistsException("Contract negotiation with providerPid " + cn.getProviderPid() + 
						" and consumerPid " + cn.getConsumerPid() + " already exists");
		});
    }
    
    protected ContractNegotiation findContractNegotiationById (String contractNegotiationId) {
    	return contractNegotiationRepository.findById(contractNegotiationId)
    	        .orElseThrow(() ->
                new ContractNegotiationNotFoundException("Contract negotiation with id " + contractNegotiationId + " not found"));
    }
    

}
