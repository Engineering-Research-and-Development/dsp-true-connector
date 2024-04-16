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
     */
    public ContractNegotiation getNegotiationByProviderPid(String providerPid) {
        log.info("Getting contract negotiation by provider pid: " + providerPid);
        publisher.publishEvent(ContractNegotiationEvent.builder().action("Find by provider pid").description("Searching with provider pid ").build());
        return repository.findByProviderPid(providerPid)
                .orElseThrow(() ->
                        new ContractNegotiationNotFoundException("Contract negotiation with provider pid " + providerPid + " not found", providerPid));
    }


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
