package it.eng.negotiation.service;


import org.springframework.stereotype.Service;

import it.eng.negotiation.event.ContractNegotiationEvent;
import it.eng.negotiation.exception.ContractNegotiationExistsException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.rest.protocol.ContactNegotiationCallback;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.contractnegotiation.ContractNegotationOfferRequest;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationProviderService {

    private ContractNegotiationPublisher publisher;
	private ContractNegotiationRepository repository;
	private OkHttpRestClient okHttpRestClient;
	private ContractNegotiationProperties properties;

    public ContractNegotiationProviderService(ContractNegotiationPublisher publisher,
			ContractNegotiationRepository repository, OkHttpRestClient okHttpRestClient,
			ContractNegotiationProperties properties) {
		this.publisher = publisher;
		this.repository = repository;
		this.okHttpRestClient = okHttpRestClient;
		this.properties = properties;
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
        return repository.findById(id)
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
        return repository.findByProviderPid(providerPid)
                .orElseThrow(() ->
                        new ContractNegotiationNotFoundException("Contract negotiation with provider pid " + providerPid + " not found", providerPid));
    }

    /**
     * Initiates a new contract negotiation based on the provided contract request and saves it in the database.
     * This method ensures that no existing contract negotiation between the same provider and consumer is active.
     * If a negotiation already exists, an exception is thrown to prevent duplication.
     *
     * @param contractRequestMessage - the contract request message containing details about the provider and consumer involved in the negotiation.
     * @return ContractNegotiation - the newly created contract negotiation record.
     * @throws ContractNegotiationExistsException if a contract negotiation already exists for the given provider and consumer PID combination.
     */
    public ContractNegotiation startContractNegotiation(ContractRequestMessage contractRequestMessage) {
        log.info("Starting contract negotiation...");
        
        repository.findByProviderPidAndConsumerPid(contractRequestMessage.getProviderPid(), contractRequestMessage.getConsumerPid())
                .ifPresent(crm -> {
                    throw new ContractNegotiationExistsException("Contract request message with provider and consumer pid's exists", contractRequestMessage.getProviderPid(), contractRequestMessage.getConsumerPid());
                });
        

        String authorization =  okhttp3.Credentials.basic("admin@mail.com", "password");
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol("http://localhost:8090/api/offer/validateOffer", Serializer.serializePlainJsonNode(contractRequestMessage.getOffer()), authorization);
        
		if (!response.isSuccess()) {
			throw new ContractNegotiationExistsException("OFFER NOT VALID", contractRequestMessage.getProviderPid(), contractRequestMessage.getConsumerPid());

		}
		
        ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
                .state(ContractNegotiationState.REQUESTED)
                .consumerPid(contractRequestMessage.getConsumerPid())
                .callbackAddress(contractRequestMessage.getCallbackAddress())
                .build();

        if(properties.isAutomaticNegotiation()) {
        	log.debug("Performing automatic negotiation");
        	publisher.publishEvent(new ContractNegotationOfferRequest(
        			cn.getConsumerPid(),
        			cn.getProviderPid(),
        			Serializer.serializeProtocolJsonNode(contractRequestMessage.getOffer())));
        } else {
        	log.debug("Offer evaluation will have to be done by human");
        }

        repository.save(cn);
        return cn;
    }

	public void finalizeNegotiation(ContractAgreementVerificationMessage cavm) {
		ContractNegotiation contractNegotiation = repository
				.findByProviderPidAndConsumerPid(cavm.getProviderPid(), cavm.getConsumerPid())
				.orElseThrow(() -> new ContractNegotiationNotFoundException(
						"Contract negotiation with provider pid " + cavm.getProviderPid() + " not found"));

		ContractNegotiationEventMessage finalize = ContractNegotiationEventMessage.Builder.newInstance()
				.consumerPid(contractNegotiation.getConsumerPid())
				.providerPid(contractNegotiation.getProviderPid())
				.eventType(ContractNegotiationEventType.FINALIZED)
				.build();
		String authorization = okhttp3.Credentials.basic("connector@mail.com", "password");
		//	https://consumer.com/:callback/negotiations/:consumerPid/events
		String callbackAddress = contractNegotiation.getCallbackAddress() + ContactNegotiationCallback.getContractEventsCallback("consumer", contractNegotiation.getConsumerPid());
		log.info("Sending ContractNegotiationEventMessage.FINALIZED to {}", callbackAddress);
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(callbackAddress, 
				Serializer.serializeProtocolJsonNode(finalize),
				authorization);
		if (response.isSuccess()) {
			ContractNegotiation contractNegtiationUpdate = ContractNegotiation.Builder.newInstance()
					.id(contractNegotiation.getId())
					.consumerPid(cavm.getConsumerPid())
					.providerPid(cavm.getProviderPid())
					.callbackAddress(contractNegotiation.getCallbackAddress())
					.state(ContractNegotiationState.FINALIZED)
					.build();
			repository.save(contractNegtiationUpdate);
		} else {
			log.info(
					"Response status not 200 - consumer did not process AgreementMessage correct - what to do with it???");
		}
	}
}
