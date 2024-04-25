package it.eng.negotiation.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationAPIService {

	private CallbackHandler callbackHandler;
	private ContractNegotiationRepository repository;
	private ContractNegotiationProperties properties;

	public ContractNegotiationAPIService(CallbackHandler callbackHandler, ContractNegotiationRepository repository,
			ContractNegotiationProperties properties) {
		this.callbackHandler = callbackHandler;
		this.repository = repository;
		this.properties = properties;
	}

	public JsonNode startNegotiation(String forwardTo, JsonNode offerNode) {
		ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
				.callbackAddress(properties.callbackAddress())
				.consumerPid("urn:uuid:" + UUID.randomUUID())
				.offer(Serializer.deserializeProtocol(offerNode, Offer.class))
				.build();
		String authorization =  okhttp3.Credentials.basic("connector@mail.com", "password");
		String response = callbackHandler.sendRequestProtocol(forwardTo, Serializer.serializeProtocolJsonNode(contractRequestMessage), authorization);
		log.info("Response received {}", response);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode jsonNode = null;
		try {
			log.info("ContractNegotiation received {}", response);
			jsonNode = mapper.readTree(response);
			ContractNegotiation contractNegotiation = Serializer.deserializeProtocol(jsonNode, ContractNegotiation.class);
			// as workaround set forwartDo in callbackAddress???
			ContractNegotiation contractNegtiationUpdate = ContractNegotiation.Builder.newInstance()
					.id(contractNegotiation.getId())
					.consumerPid(contractNegotiation.getConsumerPid())
					.providerPid(contractNegotiation.getProviderPid())
					.callbackAddress("http://localhost:8090/")
					.state(contractNegotiation.getState())
					.build();
			repository.save(contractNegtiationUpdate);
		} catch (JsonProcessingException e) {
			throw new ContractNegotiationAPIException(e.getLocalizedMessage(), e);
		}
		return jsonNode;
	}
}
