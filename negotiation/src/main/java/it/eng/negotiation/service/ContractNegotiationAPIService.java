package it.eng.negotiation.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationAPIService {

	private OkHttpRestClient okHttpRestClient;
	private ContractNegotiationRepository repository;
	private ContractNegotiationProperties properties;

	public ContractNegotiationAPIService(OkHttpRestClient okHttpRestClient, ContractNegotiationRepository repository,
			ContractNegotiationProperties properties) {
		this.okHttpRestClient = okHttpRestClient;
		this.repository = repository;
		this.properties = properties;
	}

	/**
	 * Start negotiation</br>
	 * Contract request message will be created and sent to connector behind forwardTo URL
	 * @param forwardTo - target connector URL
	 * @param offerNode - offer
	 * @return
	 */
	public JsonNode startNegotiation(String forwardTo, JsonNode offerNode) {
		ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
				.callbackAddress(properties.callbackAddress())
				.consumerPid("urn:uuid:" + UUID.randomUUID())
				.offer(Serializer.deserializePlain(offerNode.toPrettyString(), Offer.class))
				.build();
		String authorization =  okhttp3.Credentials.basic("connector@mail.com", "password");
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(forwardTo, Serializer.serializeProtocolJsonNode(contractRequestMessage), authorization);
		log.info("Response received {}", response);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode jsonNode = null;
		try {
			log.info("ContractNegotiation received {}", response);
			jsonNode = mapper.readTree(response.getData());
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

	/**
	 * Provider post offer to consumer
	 * @param forwardTo
	 * @param offerNode
	 * @return
	 */
	public JsonNode postContractOffer(String forwardTo, JsonNode offerNode) {
		ContractOfferMessage offerMessage = ContractOfferMessage.Builder.newInstance()
				.providerPid("urn:uuid:" + UUID.randomUUID())
				.callbackAddress(properties.callbackAddress())
				.offer(Serializer.deserializePlain(offerNode.toPrettyString(), Offer.class))
				.build();

		String authorization =  okhttp3.Credentials.basic("connector@mail.com", "password");
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(forwardTo, 
				Serializer.serializeProtocolJsonNode(offerMessage), authorization);
		
		/*
		 * Response from Consumer
			The Consumer must return an HTTP 201 (Created) response with a body containing the Contract Negotiation:
		{
		  "@context": "https://w3id.org/dspace/2024/1/context.json",
		  "@type": "dspace:ContractNegotiation",
		  "dspace:providerPid": "urn:uuid:dcbf434c-eacf-4582-9a02-f8dd50120fd3",
		  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
		  "dspace:state" :"OFFERED"
		}
		 */
		JsonNode jsonNode = null;
		ObjectMapper mapper = new ObjectMapper();
		try {
			if(response.isSuccess()) {
				log.info("ContractNegotiation received {}", response);
				jsonNode = mapper.readTree(response.getData());
				ContractNegotiation contractNegotiation = Serializer.deserializeProtocol(jsonNode, ContractNegotiation.class);
				ContractNegotiation contractNegtiationUpdate = ContractNegotiation.Builder.newInstance()
						.id(contractNegotiation.getId())
						.consumerPid(contractNegotiation.getConsumerPid())
						.providerPid(contractNegotiation.getProviderPid())
						.callbackAddress(forwardTo)
						.state(contractNegotiation.getState())
						.build();
				// provider saves contract negotiation
				repository.save(contractNegtiationUpdate);
			} else {
				log.info("Error response received!");
				throw new ContractNegotiationAPIException(response.getMessage());
			}
		} catch (JsonProcessingException e) {
			throw new ContractNegotiationAPIException(e.getLocalizedMessage(), e);
		}
		return jsonNode;
	}
}
