package it.eng.negotiation.service;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.transformer.from.JsonFromContractNegotiationTransformer;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationConsumerService {

	@Value("${application.connectorid}") String connectroId;
	/**
	 * {
		  "@context": "https://w3id.org/dspace/v0.8/context.json",
		  "@type": "dspace:ContractNegotiation",
		  "dspace:providerPid": "urn:uuid:dcbf434c-eacf-4582-9a02-f8dd50120fd3",
		  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
		  "dspace:state" :"OFFERED"
		}
	 * @param contractOfferMessage
	 * @return
	 */
	@Async
	public CompletableFuture<JsonNode> getContractNegotiation(ContractOfferMessage contractOfferMessage) {
		//TODO consumer side only - handle consumerPid and providerPid
		CompletableFuture<JsonNode> task =  new CompletableFuture<JsonNode>();
		ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
				.consumerPid(contractOfferMessage.getProviderPid())
				.providerPid(connectroId)
				.state(ContractNegotiationState.OFFERED)
				.build();
		JsonFromContractNegotiationTransformer toJsonTransformer = new JsonFromContractNegotiationTransformer();
		JsonNode responseNode = toJsonTransformer.transform(contractNegotiation);
		task.complete(responseNode);
		return task;
	}
}
