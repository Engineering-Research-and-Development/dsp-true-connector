package it.eng.negotiation.service;


import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import it.eng.negotiation.event.ContractNegotiationEvent;
import it.eng.negotiation.exception.ContractNegotiationExistsException;
import it.eng.negotiation.exception.ContractNegotiationInvalidStateException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.exception.OfferNotValidException;
import it.eng.negotiation.exception.ProviderPidNotBlankException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.serializer.Serializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequestEvent;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationProviderService {

    private final ContractNegotiationPublisher publisher;
	private final ContractNegotiationRepository contractNegotiationRepository;
	private final OkHttpRestClient okHttpRestClient;
	private final  ContractNegotiationProperties properties;
	private final OfferRepository offerRepository;

    public ContractNegotiationProviderService(ContractNegotiationPublisher publisher,
			ContractNegotiationRepository contractNegotiationRepository, OkHttpRestClient okHttpRestClient,
			ContractNegotiationProperties properties, OfferRepository offerRepository) {
		this.publisher = publisher;
		this.contractNegotiationRepository = contractNegotiationRepository;
		this.okHttpRestClient = okHttpRestClient;
		this.properties = properties;
		this.offerRepository = offerRepository;
	}

	/**
     * Method to get a contract negotiation by its unique identifier.
     * If no contract negotiation is found with the given ID, it throws a not found exception.
     *
     * @param id - provider pid
     * @return ContractNegotiation - contract negotiation from DB
     * @throws ContractNegotiationNotFoundException if no contract negotiation is found with the specified ID.
     */
    public ContractNegotiation getNegotiationById(String id) {
        publisher.publishEvent(ContractNegotiationEvent.builder().action("Find by id").description("Searching with id").build());
        return contractNegotiationRepository.findById(id)
                .orElseThrow(() ->
                        new ContractNegotiationNotFoundException("Contract negotiation with id " + id + " not found"));
    }

    /**
     * Method to get contract negotiation by provider pid, without callback address
     *
     * @param providerPid - provider pid
     * @return ContractNegotiation - contract negotiation from DB
     * @throws ContractNegotiationNotFoundException if no contract negotiation is found with the specified provider pid.
     */
    public ContractNegotiation getNegotiationByProviderPid(String providerPid) {
        log.info("Getting contract negotiation by provider pid: " + providerPid);
        publisher.publishEvent(ContractNegotiationEvent.builder().action("Find by provider pid").description("Searching with provider pid ").build());
        return contractNegotiationRepository.findByProviderPid(providerPid)
                .orElseThrow(() ->
                        new ContractNegotiationNotFoundException("Contract negotiation with provider pid " + providerPid + " not found", providerPid));
    }

    /**
     * Instantiates a new contract negotiation based on the consumer contract request and saves it in the database.
     * This method ensures that no existing contract negotiation between the same provider and consumer is active.
     * If a negotiation already exists, an exception is thrown to prevent duplication.
     *
     * @param contractRequestMessage - the contract request message containing details about the provider and consumer involved in the negotiation.
     * @return ContractNegotiation - the newly created contract negotiation record.
     * @throws InterruptedException 
     * @throws ContractNegotiationExistsException if a contract negotiation already exists for the given provider and consumer PID combination.
     */
    public ContractNegotiation startContractNegotiation(ContractRequestMessage contractRequestMessage) throws InterruptedException {
        log.info("PROVIDER - Starting contract negotiation...");
        
        if (StringUtils.isNotBlank(contractRequestMessage.getProviderPid())) {
        	throw new ProviderPidNotBlankException("Contract negotiation failed - providerPid has to be blank", contractRequestMessage.getConsumerPid());
        }
        
        contractNegotiationRepository.findByProviderPidAndConsumerPid(contractRequestMessage.getProviderPid(), contractRequestMessage.getConsumerPid())
                .ifPresent(crm -> {
                    throw new ContractNegotiationExistsException("PROVIDER - Contract request message with provider and consumer pid's exists", contractRequestMessage.getProviderPid(), contractRequestMessage.getConsumerPid());
                });
        

        String authorization =  okhttp3.Credentials.basic("admin@mail.com", "password");
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol("http://localhost:" + properties.serverPort() + "/api/offer/validateOffer", Serializer.serializePlainJsonNode(contractRequestMessage.getOffer()), authorization);
        
		if (!response.isSuccess()) {
			throw new OfferNotValidException("Contract offer is not valid", contractRequestMessage.getConsumerPid(), contractRequestMessage.getProviderPid());
		}
		
        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .state(ContractNegotiationState.REQUESTED)
                .consumerPid(contractRequestMessage.getConsumerPid())
                .callbackAddress(contractRequestMessage.getCallbackAddress())
                .build();

        
        contractNegotiationRepository.save(contractNegotiation);
        log.info("PROVIDER - Contract negotiation {} saved", contractNegotiation.getId());
        Offer dbOffer = Offer.Builder.newInstance()
			.id(contractRequestMessage.getOffer().getId())
			.permission(contractRequestMessage.getOffer().getPermission())
			.target(contractRequestMessage.getOffer().getTarget())
			.consumerPid(contractNegotiation.getConsumerPid())
			.providerPid(contractNegotiation.getProviderPid())
			.build();
		offerRepository.save(dbOffer);
		log.info("PROVIDER - Offer {} saved", contractRequestMessage.getOffer().getId());
		if (properties.isAutomaticNegotiation()) {
			log.debug("PROVIDER - Performing automatic negotiation");
			publisher.publishEvent(new ContractNegotationOfferRequestEvent(
					contractNegotiation.getConsumerPid(),
					contractNegotiation.getProviderPid(),
					Serializer.serializeProtocolJsonNode(contractRequestMessage.getOffer())));
		} else {
			log.debug("PROVIDER - Offer evaluation will have to be done by human");
		}
        return contractNegotiation;
    }

	public void verifyNegotiation(ContractAgreementVerificationMessage cavm) {
		ContractNegotiation contractNegotiation = contractNegotiationRepository.findByProviderPidAndConsumerPid(cavm.getProviderPid(), cavm.getConsumerPid())
				.orElseThrow(() -> new ContractNegotiationNotFoundException(
						"Contract negotiation with providerPid " + cavm.getProviderPid() + 
						" and consumerPid " + cavm.getConsumerPid() + " not found", cavm.getConsumerPid(), cavm.getProviderPid()));

		if (!contractNegotiation.getState().canTransitTo(ContractNegotiationState.VERIFIED)) {
			throw new ContractNegotiationInvalidStateException(
					"Contract negotiation with providerPid " + cavm.getProviderPid() + 
					" and consumerPid " + cavm.getConsumerPid() + " is not in AGREED state, aborting verification", cavm.getConsumerPid(), cavm.getProviderPid());
		}

		ContractNegotiation contractNegotiationUpdated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.VERIFIED);
		contractNegotiationRepository.save(contractNegotiationUpdated);
		log.info("Contract negotiation with providerPid {} and consumerPid {} changed state to VERIFIED and saved", cavm.getProviderPid(), cavm.getConsumerPid());
	}
}
