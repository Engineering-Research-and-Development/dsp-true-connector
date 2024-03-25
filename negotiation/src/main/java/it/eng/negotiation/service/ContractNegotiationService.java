package it.eng.negotiation.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.entity.ContractNegotiationEntity;
import it.eng.negotiation.event.ContractNegotiationEvent;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.*;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.transformer.from.JsonFromContractNegotiationErrorMessageTrasformer;
import it.eng.negotiation.transformer.from.JsonFromContractNegotiationTransformer;
import it.eng.tools.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Service
@Slf4j
public class ContractNegotiationService {

    private ContractNegotiationPublisher publisher;
    private ContractNegotiationRepository repository;

    private String connectorId;
    private boolean isConsumer;

    public ContractNegotiationService(ContractNegotiationPublisher publisher, ContractNegotiationRepository repository,
                                      @Value("${application.connectorid}") String connectorId,
                                      @Value("${application.isconsumer}") boolean isConsumer) {
        super();
        this.publisher = publisher;
        this.repository = repository;
        this.connectorId = connectorId;
        this.isConsumer = isConsumer;
    }

    public ContractNegotiation getNegotiationById(String id) {
        publisher.publishEvent(ContractNegotiationEvent.builder().action("Find by id").description("Searching with id").build());
        return repository.findById(id)
                .map(this::entityToModel)
                .orElseThrow(() ->
                        new ContractNegotiationNotFoundException("Contract negotiation with id " + id + " not found"));
    }

    /**
     * Method to get contract negotiation by provider pid, without calback address
     *
     * @param providerPid - provider pid
     * @return CompletableFuture - contract negotiation from DB
     */
    @Async("asyncExecutor")
    public CompletableFuture<ContractNegotiation> getNegotiationByProviderPid(String providerPid) {
        publisher.publishEvent(ContractNegotiationEvent.builder().action("Find by provider pid async").description("Searching with provider pid async").build());
        return CompletableFuture.supplyAsync(() -> repository.findByProviderPid(providerPid)
                .map(this::entityToModel)
                .orElseThrow(() ->
                        new ContractNegotiationNotFoundException("Contract negotiation with provider pid " + providerPid + " not found", providerPid)));
    }

    @Async("asyncExecutor")
    public Future<ContractNegotiation> startContractNegotiationOld(ContractRequestMessage contractRequestMessage) {
        CompletableFuture<ContractNegotiation> task = new CompletableFuture<ContractNegotiation>();
        log.info("Starting contract negotiation...");
        ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                .state(ContractNegotiationState.REQUESTED)
                .consumerPid(contractRequestMessage.getConsumerPid())
                .build();

        task.complete(contractNegotiation);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        log.info("Created contract negotiation REQUESTED");
        return task;
    }

    @Async
    public CompletableFuture<JsonNode> startContractNegotiation(ContractRequestMessage contractRequestMessage) {
        log.info("Starting contract negotiation...");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return CompletableFuture.supplyAsync(() -> {
            repository.findByProviderPidAndConsumerPid(contractRequestMessage.getProviderPid(), contractRequestMessage.getConsumerPid())
                    .ifPresent(crm -> {
                        throw new BadRequestException("Contract reuqest message with provider and consumer pid's exists");
                    });
            ContractNegotiation result = ContractNegotiation.Builder.newInstance()
                    .state(ContractNegotiationState.REQUESTED)
                    .consumerPid(contractRequestMessage.getConsumerPid())
                    .providerPid(connectorId)
                    .build();
            JsonFromContractNegotiationTransformer jsonFromContractNegotiationTransformer = new JsonFromContractNegotiationTransformer();
            ContractNegotiationEntity cnEntity = new ContractNegotiationEntity();
//			cnEntity.setId(result.getId());
            cnEntity.setState(result.getState().toString());
            cnEntity.setConsumerPid(result.getConsumerPid());
            cnEntity.setProviderPid(connectorId);
            repository.save(cnEntity);
            return jsonFromContractNegotiationTransformer.transform(result);
        }).exceptionallyAsync(ex -> {
            log.error("{} {}", "Exception", ex.getLocalizedMessage());
            ContractNegotiationErrorMessage.Builder errorMessageBuilder = ContractNegotiationErrorMessage.Builder.newInstance()
                    // TODO check if consumer and privider are set correct
                    .consumerPid(contractRequestMessage.getConsumerPid())
                    .providerPid(connectorId)
                    .code(HttpStatus.NOT_FOUND.getReasonPhrase())
                    .reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("Error something is not right").build()))
                    .description(Arrays.asList(Description.Builder.newInstance().language("en").value("Error something is not right").build()));
            if (isConsumer) {
                errorMessageBuilder.consumerPid(connectorId);
            } else {
                errorMessageBuilder.providerPid(connectorId);
            }
            JsonFromContractNegotiationErrorMessageTrasformer transformer = new JsonFromContractNegotiationErrorMessageTrasformer();
            JsonNode bodyOfResponse = transformer.transform(errorMessageBuilder.build());
            return bodyOfResponse;
        });
    }

    @Async("asyncExecutor")
    public Future<JsonNode> startContractNegotiationNode(ContractRequestMessage contractRequestMessage) {
        CompletableFuture<JsonNode> task = new CompletableFuture<JsonNode>();
        log.info("Starting contract negotiation...");
        if ((Math.random() % 2) == 0) {
            ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
                    .state(ContractNegotiationState.REQUESTED)
                    .consumerPid(contractRequestMessage.getConsumerPid())
                    .build();

            JsonFromContractNegotiationTransformer jsonFromContractNegotiationTransformer = new JsonFromContractNegotiationTransformer();
            JsonNode jsonNode = jsonFromContractNegotiationTransformer.transform(contractNegotiation);
            task.complete(jsonNode);
        } else {
            ContractNegotiationErrorMessage.Builder errorMessageBuilder = ContractNegotiationErrorMessage.Builder.newInstance()
                    .code(HttpStatus.NOT_FOUND.getReasonPhrase())
                    .reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("Error something is not right").build()))
                    .description(Arrays.asList(Description.Builder.newInstance().language("en").value("Error something is not right").build()));
            if (isConsumer) {
                errorMessageBuilder.consumerPid(connectorId);
            } else {
                errorMessageBuilder.providerPid(connectorId);
            }
            task.completeExceptionally(new ContractNegotiationNotFoundException("dasdasd"));
//					task.complete(bodyOfResponse);
        }
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        log.info("Created contract negotiation REQUESTED");
        return task;
    }

    private ContractNegotiation entityToModel(ContractNegotiationEntity entity) {
        return ContractNegotiation.Builder.newInstance()
                .state(ContractNegotiationState.valueOf(entity.getState()))
                .consumerPid(entity.getConsumerPid())
                .providerPid(entity.getProviderPid())
                .build();
    }
}
