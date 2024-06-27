package it.eng.negotiation.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.rest.protocol.ContractNegotiationCallback;
import it.eng.negotiation.serializer.Serializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationAPIService {

	private final OkHttpRestClient okHttpRestClient;
	private final ContractNegotiationRepository contractNegotiationRepository;
	private final ContractNegotiationProperties properties;
	private final OfferRepository offerRepository;
	private final AgreementRepository agreementRepository;
	ObjectMapper mapper = new ObjectMapper();

	public ContractNegotiationAPIService(OkHttpRestClient okHttpRestClient, ContractNegotiationRepository contractNegotiationRepository,
			ContractNegotiationProperties properties, OfferRepository offerRepository, AgreementRepository agreementRepository) {
		this.okHttpRestClient = okHttpRestClient;
		this.contractNegotiationRepository = contractNegotiationRepository;
		this.properties = properties;
		this.offerRepository = offerRepository;
		this.agreementRepository = agreementRepository;
	}

	/**
	 * Start negotiation as consumer</br>
	 * Contract request message will be created and sent to connector behind forwardTo URL
	 * @param forwardTo - target connector URL
	 * @param offerNode - offer
	 * @return
	 */
	public JsonNode startNegotiation(String forwardTo, JsonNode offerNode) {
		Offer offer = Serializer.deserializePlain(offerNode.toPrettyString(), Offer.class);
		ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
				.callbackAddress(properties.consumerCallbackAddress())
				.consumerPid("urn:uuid:" + UUID.randomUUID())
				.offer(offer)
				.build();
		String authorization =  okhttp3.Credentials.basic("connector@mail.com", "password");
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(forwardTo, Serializer.serializeProtocolJsonNode(contractRequestMessage), authorization);
		log.info("Response received {}", response);
		ContractNegotiation contractNegotiation = null;
		if (response.getHttpStatus() == 201) {
			try {
				JsonNode jsonNode = mapper.readTree(response.getData());
				contractNegotiation = Serializer.deserializeProtocol(jsonNode, ContractNegotiation.class);
				contractNegotiationRepository.save(contractNegotiation);
				log.info("Contract negotiation {} saved", contractNegotiation.getId());
				Offer dbOffer = Offer.Builder.newInstance()
						.id(offer.getId())
						.permission(offer.getPermission())
						.target(offer.getTarget())
						.consumerPid(contractNegotiation.getConsumerPid())
						.providerPid(contractNegotiation.getProviderPid())
						.build();
				offerRepository.save(dbOffer);
				log.info("Offer {} saved", offer.getId());
			} catch (JsonProcessingException e) {
				log.error("Contract negotiation from response not valid");
				throw new ContractNegotiationAPIException(e.getLocalizedMessage(), e);
			}
		} else {
			log.info("Error response received!");
			throw new ContractNegotiationAPIException(response.getMessage());
		}
		return Serializer.serializePlainJsonNode(contractNegotiation);
	}

	/**
	 * Provider sends offer to consumer
	 * @param forwardTo
	 * @param offerNode
	 * @return
	 */
	public JsonNode sendContractOffer(String forwardTo, JsonNode offerNode) {
		ContractOfferMessage offerMessage = ContractOfferMessage.Builder.newInstance()
				.providerPid("urn:uuid:" + UUID.randomUUID())
				.callbackAddress(properties.providerCallbackAddress())
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
						// callbackAddress is the same because it is now Consumer's turn to respond
//						.callbackAddress(forwardTo)
						.state(contractNegotiation.getState())
						.build();
				// provider saves contract negotiation
				contractNegotiationRepository.save(contractNegtiationUpdate);
			} else {
				log.info("Error response received!");
				throw new ContractNegotiationAPIException(response.getMessage());
			}
		} catch (JsonProcessingException e) {
			throw new ContractNegotiationAPIException(e.getLocalizedMessage(), e);
		}
		return jsonNode;
	}

	public void sendAgreement(String consumerPid, String providerPid, JsonNode agreementNode) {
		ContractNegotiation contractNegotiation = contractNegotiationRepository.findByProviderPidAndConsumerPid(providerPid, consumerPid)
				.orElseThrow(() -> new ContractNegotiationAPIException(
						"Contract negotiation with providerPid " + providerPid + 
						" and consumerPid " + consumerPid + " not found"));
		
		if (!contractNegotiation.getState().equals(ContractNegotiationState.REQUESTED)
    			&& !contractNegotiation.getState().equals(ContractNegotiationState.ACCEPTED)) {
			throw new ContractNegotiationAPIException("Agreement aborted, wrong state " + contractNegotiation.getState().name());
		}
		
		Agreement agreement = Serializer.deserializePlain(agreementNode.toPrettyString(), Agreement.class);
		ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.callbackAddress(properties.providerCallbackAddress())
				.agreement(agreement)
				.build();
		
    	log.info("Sending agreement as provider to {}", contractNegotiation.getCallbackAddress());
		String authorization =  okhttp3.Credentials.basic("connector@mail.com", "password");
		GenericApiResponse<String> response = okHttpRestClient
				.sendRequestProtocol(ContractNegotiationCallback.getContractAgreementCallback(contractNegotiation.getCallbackAddress(), consumerPid),
				Serializer.serializeProtocolJsonNode(agreementMessage),
				authorization);
		log.info("Response received {}", response);
		if (response.getHttpStatus() == 200) {
			ContractNegotiation contractNegotiationStateChange = ContractNegotiation.Builder.newInstance()
					.id(contractNegotiation.getId())
					.callbackAddress(contractNegotiation.getCallbackAddress())
					.consumerPid(contractNegotiation.getConsumerPid())
					.providerPid(contractNegotiation.getProviderPid())
					.state(ContractNegotiationState.AGREED)
					.build();
			
			contractNegotiationRepository.save(contractNegotiationStateChange);
			log.info("Contract negotiation {} saved", contractNegotiation.getId());
			agreementRepository.save(agreement);
			log.info("Agreement {} saved", agreement.getId());
		} else {
			log.error("Error response received!");
			throw new ContractNegotiationAPIException(response.getMessage());
		}
	}

	public void finalizeNegotiation(ContractNegotiationEventMessage contractNegotiationEventMessage) {
		ContractNegotiation contractNegotiation = contractNegotiationRepository
				.findByProviderPidAndConsumerPid(contractNegotiationEventMessage.getProviderPid(), contractNegotiationEventMessage.getConsumerPid())
				.orElseThrow(() -> new ContractNegotiationAPIException("Contract negotiation with providerPid "
						+ contractNegotiationEventMessage.getProviderPid()
						+ " and consumerPid " + contractNegotiationEventMessage.getConsumerPid()
						+ " not found"));

		if (!contractNegotiation.getState().equals(ContractNegotiationState.VERIFIED)) {
			throw new ContractNegotiationAPIException(
					"Finalization aborted, wrong state " + contractNegotiation.getState().name());
		}
		
		String authorization = okhttp3.Credentials.basic("connector@mail.com", "password");
		//	https://consumer.com/:callback/negotiations/:consumerPid/events
		String callbackAddress = ContractNegotiationCallback.getContractEventsCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getConsumerPid());
		
		log.info("Sending ContractNegotiationEventMessage.FINALIZED to {}", callbackAddress);
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(callbackAddress,
				Serializer.serializeProtocolJsonNode(contractNegotiationEventMessage), authorization);
		
		if (response.isSuccess()) {
			ContractNegotiation contractNegtiationUpdate = ContractNegotiation.Builder.newInstance()
					.id(contractNegotiation.getId())
					.consumerPid(contractNegotiationEventMessage.getConsumerPid())
					.providerPid(contractNegotiationEventMessage.getProviderPid())
					.callbackAddress(contractNegotiation.getCallbackAddress())
					.state(ContractNegotiationState.FINALIZED)
					.build();
			contractNegotiationRepository.save(contractNegtiationUpdate);
		} else {
			log.error("Error response received!");
			throw new ContractNegotiationAPIException(response.getMessage());
		}
	}
}
