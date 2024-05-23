package it.eng.negotiation.service;


import it.eng.negotiation.event.ContractNegotiationEvent;
import it.eng.negotiation.exception.ContractNegotiationExistsException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class ContractNegotiationService {

    private ContractNegotiationPublisher publisher;
    private ContractNegotiationRepository repository;


    public ContractNegotiationService(ContractNegotiationPublisher publisher, ContractNegotiationRepository repository) {
        super();
        this.publisher = publisher;
        this.repository = repository;
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

        ContractNegotiation cn = ContractNegotiation.Builder.newInstance()
                .state(ContractNegotiationState.REQUESTED)
                .consumerPid(contractRequestMessage.getConsumerPid())
                .build();

        repository.save(cn);
        return cn;
    }
}