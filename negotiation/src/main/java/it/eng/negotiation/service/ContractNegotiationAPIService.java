package it.eng.negotiation.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Reason;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.rest.protocol.ContractNegotiationCallback;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.policy.PolicyEnforcementService;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.datatransfer.InitializeTransferProcess;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationAPIService {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private final OkHttpRestClient okHttpRestClient;
	private final ContractNegotiationRepository contractNegotiationRepository;
	private final ContractNegotiationProperties properties;
	private final OfferRepository offerRepository;
	private final AgreementRepository agreementRepository;
	private final CredentialUtils credentialUtils;
	private final ObjectMapper mapper = new ObjectMapper();
	private final PolicyEnforcementService policyEnforcementService;
	private final ContractNegotiationPublisher publisher;

	public ContractNegotiationAPIService(OkHttpRestClient okHttpRestClient, ContractNegotiationRepository contractNegotiationRepository,
			ContractNegotiationProperties properties, OfferRepository offerRepository, AgreementRepository agreementRepository,
			CredentialUtils credentialUtils, PolicyEnforcementService policyEnforcementService, ContractNegotiationPublisher publisher) {
		this.okHttpRestClient = okHttpRestClient;
		this.contractNegotiationRepository = contractNegotiationRepository;
		this.properties = properties;
		this.offerRepository = offerRepository;
		this.agreementRepository = agreementRepository;
		this.credentialUtils = credentialUtils;
		this.policyEnforcementService = policyEnforcementService;
		this.publisher = publisher;
	}

	public Collection<JsonNode> findContractNegotiations(String contractNegotiationId, String state, String role, String consumerPid, String providerPid) {
		if (StringUtils.isNotBlank(contractNegotiationId)) {
			return contractNegotiationRepository.findById(contractNegotiationId)
					.stream()
					.map(cn -> NegotiationSerializer.serializePlainJsonNode(cn))
					.collect(Collectors.toList());
		} else if (StringUtils.isNotBlank(state)) {
			return contractNegotiationRepository.findByStateAndRole(state, role)
					.stream()
					.map(cn -> NegotiationSerializer.serializePlainJsonNode(cn))
					.collect(Collectors.toList());
		} else if(StringUtils.isNotBlank(consumerPid) && StringUtils.isNotBlank(providerPid)) {
			return contractNegotiationRepository.findByProviderPidAndConsumerPid(providerPid, consumerPid)
					.stream()
					.map(cn -> NegotiationSerializer.serializePlainJsonNode(cn))
					.collect(Collectors.toList());
		}
		return contractNegotiationRepository.findByRole(role)
				.stream()
				.map(cn -> NegotiationSerializer.serializePlainJsonNode(cn))
				.collect(Collectors.toList());
	}

	/**
	 * Start negotiation as consumer.<br>
	 * Contract request message will be created and sent to connector behind forwardTo URL
	 * @param forwardTo - target connector URL
	 * @param offerNode - offer
	 * @return ContractNegotiation as JsonNode
	 */
	public JsonNode startNegotiation(String forwardTo, JsonNode offerNode) {
		Offer offer = NegotiationSerializer.deserializePlain(offerNode.toPrettyString(), Offer.class);
		ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
				.callbackAddress(properties.consumerCallbackAddress())
				.consumerPid("urn:uuid:" + UUID.randomUUID())
				.offer(offer)
				.build();
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(ContractNegotiationCallback.getNegotiationRequestURL(forwardTo), 
				NegotiationSerializer.serializeProtocolJsonNode(contractRequestMessage), credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		ContractNegotiation contractNegotiationWithOffer = null;
		if (response.isSuccess()) {
			try {
				JsonNode jsonNode = mapper.readTree(response.getData());
				ContractNegotiation contractNegotiation = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiation.class);
				
				Offer offerToBeInserted = Offer.Builder.newInstance()
						.assignee(offer.getAssignee())
						.assigner(offer.getAssigner())
						.originalId(offer.getId())
						.permission(offer.getPermission())
						.target(offer.getTarget())
						.build();
				
				Offer savedOffer = offerRepository.save(offerToBeInserted);
				log.info("Offer {} saved", savedOffer.getId());
				String callbackAddress = contractNegotiation.getCallbackAddress();
				if(StringUtils.isBlank(callbackAddress)) {
					log.debug("Response ContractNegotiation.callbackAddress is null, setting it to forwardTo");
					callbackAddress = forwardTo;
				}
				contractNegotiationWithOffer = ContractNegotiation.Builder.newInstance()
						.id(contractNegotiation.getId())
		    			.consumerPid(contractNegotiation.getConsumerPid())
		    			.providerPid(contractNegotiation.getProviderPid())
		    			.callbackAddress(callbackAddress)
		    			.assigner(offer.getAssigner())
		    			.state(contractNegotiation.getState())
		    			.role(IConstants.ROLE_CONSUMER)
		    			.offer(savedOffer)
						.build();
				contractNegotiationRepository.save(contractNegotiationWithOffer);
				log.info("Contract negotiation {} saved", contractNegotiationWithOffer.getId());
			} catch (JsonProcessingException e) {
				log.error("Contract negotiation from response not valid");
				throw new ContractNegotiationAPIException(e.getLocalizedMessage(), e);
			}
		} else {
			log.info("Error response received!");
			JsonNode jsonNode;
			try {
				jsonNode = mapper.readTree(response.getData());
				ContractNegotiationErrorMessage contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiationErrorMessage.class);
				throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, "Error making request");
			} catch (JsonProcessingException e) {
				throw new ContractNegotiationAPIException("Error occured");
			}
		}
		return NegotiationSerializer.serializePlainJsonNode(contractNegotiationWithOffer);
	}

	/**
	 * Provider sends offer to consumer.
	 * @param forwardTo
	 * @param offerNode
	 * @return ContractNegotiation as JsonNode
	 */
	public JsonNode sendContractOffer(String forwardTo, JsonNode offerNode) {
		Offer offer = NegotiationSerializer.deserializePlain(offerNode.toPrettyString(), Offer.class);
		ContractOfferMessage offerMessage = ContractOfferMessage.Builder.newInstance()
				.providerPid("urn:uuid:" + UUID.randomUUID())
				.callbackAddress(properties.providerCallbackAddress())
				.offer(offer)
				.build();
		
		// this offer check
//		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol("http://localhost:" + properties.serverPort() + "/api/offer/validateOffer", 
//				NegotiationSerializer.serializePlainJsonNode(offer), 
//				credentialUtils.getAPICredentials());

		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(forwardTo, 
				NegotiationSerializer.serializeProtocolJsonNode(offerMessage), credentialUtils.getConnectorCredentials());
		
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
		try {
			if(response.isSuccess()) {
				log.info("ContractNegotiation received {}", response);
				jsonNode = mapper.readTree(response.getData());
				ContractNegotiation contractNegotiation = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiation.class);
				ContractNegotiation contractNegtiationUpdate = ContractNegotiation.Builder.newInstance()
						.id(contractNegotiation.getId())
						.consumerPid(contractNegotiation.getConsumerPid())
						.providerPid(contractNegotiation.getProviderPid())
						//TODO when this is solved activate the offer check from above
						// callbackAddress is the same because it is now Consumer's turn to respond
//						.callbackAddress(forwardTo)
						.assigner(offer.getAssigner())
						.role(IConstants.ROLE_PROVIDER)
						.offer(offer)
						.state(contractNegotiation.getState())
						.build();
				// provider saves contract negotiation
				contractNegotiationRepository.save(contractNegtiationUpdate);
				processContractOffer(offer);
			} else {
				log.info("Error response received!");
				throw new ContractNegotiationAPIException(response.getMessage());
			}
		} catch (JsonProcessingException e) {
			throw new ContractNegotiationAPIException(e.getLocalizedMessage(), e);
		}
		return jsonNode;
	}

	@Deprecated
	public void sendAgreement(String consumerPid, String providerPid, JsonNode agreementNode) {
		ContractNegotiation contractNegotiation = findContractNegotiationByPids(consumerPid, providerPid);
		
		stateTransitionCheck(ContractNegotiationState.AGREED, contractNegotiation.getState());
		
		Agreement agreement = NegotiationSerializer.deserializePlain(agreementNode.toPrettyString(), Agreement.class);
		ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.callbackAddress(properties.providerCallbackAddress())
				.agreement(agreement)
				.build();
		
    	log.info("Sending agreement as provider to {}", contractNegotiation.getCallbackAddress());
		GenericApiResponse<String> response = okHttpRestClient
				.sendRequestProtocol(ContractNegotiationCallback.getContractAgreementCallback(contractNegotiation.getCallbackAddress(), consumerPid),
				NegotiationSerializer.serializeProtocolJsonNode(agreementMessage),
				credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		if (response.isSuccess()) {
			ContractNegotiation contractNegotiationUpdated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.AGREED);
			contractNegotiationRepository.save(contractNegotiationUpdated);
			log.info("Contract negotiation {} saved", contractNegotiation.getId());
			agreementRepository.save(agreement);
			log.info("Agreement {} saved", agreement.getId());
		} else {
			log.error("Error response received!");
			throw new ContractNegotiationAPIException(response.getMessage());
		}
	}

	/**
	 * Finalize negotiation.
	 * @param contractNegotiationId
	 */
	public void finalizeNegotiation(String contractNegotiationId) {
		ContractNegotiation contractNegotiation = findContractNegotiationById(contractNegotiationId);

		stateTransitionCheck(ContractNegotiationState.FINALIZED, contractNegotiation.getState());
		
		 ContractNegotiationEventMessage  contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
					.consumerPid(contractNegotiation.getConsumerPid())
					.providerPid(contractNegotiation.getProviderPid())
					.eventType(ContractNegotiationEventType.FINALIZED)
					.build();
		
		//	https://consumer.com/:callback/negotiations/:consumerPid/events
		String callbackAddress = ContractNegotiationCallback.getContractEventsCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getConsumerPid());
		
		log.info("Sending ContractNegotiationEventMessage.FINALIZED to {}", callbackAddress);
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(callbackAddress,
				NegotiationSerializer.serializeProtocolJsonNode(contractNegotiationEventMessage), credentialUtils.getConnectorCredentials());
		
		if (response.isSuccess()) {
			ContractNegotiation contractNegotiationFinalized = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.FINALIZED);
			contractNegotiationRepository.save(contractNegotiationFinalized);
			// TODO remove this line once api/getArtifact is implemented on consumer side 
			policyEnforcementService.createPolicyEnforcement(contractNegotiation.getAgreement().getId());
			publisher.publishEvent(new InitializeTransferProcess(
					contractNegotiationFinalized.getCallbackAddress(),
					contractNegotiationFinalized.getAgreement().getId(),
					contractNegotiationFinalized.getAgreement().getTarget(),
					contractNegotiationFinalized.getRole()
					));
		} else {
			log.error("Error response received!");
			throw new ContractNegotiationAPIException(response.getMessage());
		}
	}

	/**
	 * Consumer sends ContractNegotiationEventMessage with state ACCEPTED.<br>
	 * Updates state Contract Negotiation upon successful response to ACCEPTED
	 * @param contractNegotiationId
	 * @return ContractNegotiation
	 */
	public ContractNegotiation handleContractNegotiationAccepted(String contractNegotiationId) {
		ContractNegotiation contractNegotiation = findContractNegotiationById(contractNegotiationId);
		
		stateTransitionCheck(ContractNegotiationState.ACCEPTED, contractNegotiation.getState());
		
		ContractNegotiationEventMessage eventMessageAccepted = ContractNegotiationEventMessage.Builder.newInstance()
				.consumerPid(contractNegotiation.getConsumerPid())
				.providerPid(contractNegotiation.getProviderPid())
				.eventType(ContractNegotiationEventType.ACCEPTED)
				.build();
		
	   	log.info("Sending ContractNegotiationEventMessage.ACCEPTED as consumer to {}", contractNegotiation.getCallbackAddress());
		GenericApiResponse<String> response = okHttpRestClient
				.sendRequestProtocol(ContractNegotiationCallback.getContractEventsCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getProviderPid()),
				NegotiationSerializer.serializeProtocolJsonNode(eventMessageAccepted),
				credentialUtils.getConnectorCredentials());
		log.info("Response received {}", response);
		if (response.isSuccess()) {
			ContractNegotiation contractNegotiationAccepted = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.ACCEPTED);
			contractNegotiationRepository.save(contractNegotiationAccepted);
			log.info("Contract negotiation {} saved", contractNegotiation.getId());
			return contractNegotiationAccepted;
		} else {
			log.error("Error response received!");
			throw new ContractNegotiationAPIException(response.getMessage());
		}
	}
	
	/**
	 * Negotiate status to AGREED after successful response from connector.
	 * @param contractNegotiationId
	 * @return ContractNegotiation
	 */
	public ContractNegotiation approveContractNegotiation(String contractNegotiationId) {
		ContractNegotiation contractNegotiation = findContractNegotiationById(contractNegotiationId);
	
		stateTransitionCheck(ContractNegotiationState.AGREED, contractNegotiation.getState());

		ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
				.consumerPid(contractNegotiation.getConsumerPid())
				.providerPid(contractNegotiation.getProviderPid())
				.callbackAddress(properties.providerCallbackAddress())
				.agreement(agreementFromOffer(contractNegotiation.getOffer(), contractNegotiation.getAssigner()))
				.build();
		// TODO this one will fail because provider does not have consumer callbackAddress for sending agreement
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
				ContractNegotiationCallback.getContractAgreementCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getConsumerPid()), 
				NegotiationSerializer.serializeProtocolJsonNode(agreementMessage),
				credentialUtils.getConnectorCredentials());
		if(response.isSuccess()) {
			log.info("Updating status for negotiation {} to agreed", contractNegotiation.getId());
			log.info("Saving agreement..." + agreementMessage.getAgreement().getId());
			agreementRepository.save(agreementMessage.getAgreement());
			
			ContractNegotiation contractNegtiationAgreed = ContractNegotiation.Builder.newInstance()
					.id(contractNegotiation.getId())
	    			.consumerPid(contractNegotiation.getConsumerPid())
	    			.providerPid(contractNegotiation.getProviderPid())
	    			.callbackAddress(contractNegotiation.getCallbackAddress())
	    			.assigner(contractNegotiation.getAssigner())
	    			.state(ContractNegotiationState.AGREED)
	    			.role(contractNegotiation.getRole())
	    			.offer(contractNegotiation.getOffer())
	    			.agreement(agreementMessage.getAgreement())
					.build();
					
			contractNegotiationRepository.save(contractNegtiationAgreed);
			return contractNegtiationAgreed;
		} else {
			log.error("Response status not 200 - consumer did not process AgreementMessage correct");
			throw new ContractNegotiationAPIException("consumer did not process AgreementMessage correct");
		}
	}
	
	/**
	 * Verify negotiation.<br>
	 * If all ok state is transitioned to VERIFIED
	 * @param contractNegotiationId
	 */
	public void verifyNegotiation(String contractNegotiationId) {
		ContractNegotiation contractNegotiation =  findContractNegotiationById(contractNegotiationId);
		
		stateTransitionCheck(ContractNegotiationState.VERIFIED, contractNegotiation.getState());
		
		ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder.newInstance()
				.consumerPid(contractNegotiation.getConsumerPid())
				.providerPid(contractNegotiation.getProviderPid())
				.build();
		
		log.info("Found intial negotiation" + " - CallbackAddress " + contractNegotiation.getCallbackAddress());

		String callbackAddress = ContractNegotiationCallback.getProviderAgreementVerificationCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getProviderPid());
		log.info("Sending verification message to provider to {}", callbackAddress);
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(callbackAddress, 
				NegotiationSerializer.serializeProtocolJsonNode(verificationMessage),
				credentialUtils.getConnectorCredentials());
		
		if(response.isSuccess()) {
			log.info("Updating status for negotiation {} to verified", contractNegotiation.getId());
			ContractNegotiation contractNegtiationVerified = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.VERIFIED);
			contractNegotiationRepository.save(contractNegtiationVerified);
		} else {
			log.error("Response status not 200 - provider did not process Verification message correct");
			JsonNode jsonNode;
			try {
				jsonNode = mapper.readTree(response.getData());
				ContractNegotiationErrorMessage contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiationErrorMessage.class);
				throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, "Provider did not process Verification message correct");
			} catch (JsonProcessingException e) {
				throw new ContractNegotiationAPIException("Provider did not process Verification message correct");
			}
		}
	}
	
	/**
	 * Terminate contract negotiation.
	 * Transition state to TERMINATED
	 * @param contractNegotiationId
	 * @return ContractNegotiation
	 */
	public ContractNegotiation handleContractNegotiationTerminated(String contractNegotiationId) {
		ContractNegotiation contractNegotiation = findContractNegotiationById(contractNegotiationId);
		// for now just log it; maybe we can publish event?
		log.info("Contract negotiation with consumerPid {} and providerPid {} declined", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
		String reason = null;
		String address = null;
		if(IConstants.ROLE_PROVIDER.equals(contractNegotiation.getRole())) {
			log.info("Terminating negotiation by provider");
			reason = "Contract negotiation terminated by provider";
			// send request to consumer callback address
			address = ContractNegotiationCallback.getContractTerminationCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getConsumerPid());
		} else {
			log.info("Terminating negotiation by consumer");
			reason = "Contract negotiation terminated by consumer";
			// send request to protocol address
			address = ContractNegotiationCallback.getContractTerminationProvider(contractNegotiation.getCallbackAddress(), 
					contractNegotiation.getProviderPid());
		}
		ContractNegotiationTerminationMessage negotiationTerminatedEventMessage = ContractNegotiationTerminationMessage.Builder.newInstance()
				.consumerPid(contractNegotiation.getConsumerPid())
				.providerPid(contractNegotiation.getProviderPid())
				.code(contractNegotiationId)
				.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value(reason).build()))
				.build();
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
				address, 
				NegotiationSerializer.serializeProtocolJsonNode(negotiationTerminatedEventMessage),
				credentialUtils.getConnectorCredentials());
		if(response.isSuccess()) {
			log.info("Updating status for negotiation {} to terminated", contractNegotiation.getId());
			ContractNegotiation contractNegtiationTerminated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.TERMINATED);
			contractNegotiationRepository.save(contractNegtiationTerminated);
			return contractNegtiationTerminated;
		} else {
			log.error("Response status not 200 - " + contractNegotiation.getRole() + " did not process ContractNegotiationTerminationMessage correct");
			JsonNode jsonNode;
			try {
				jsonNode = mapper.readTree(response.getData());
				ContractNegotiationErrorMessage contractNegotiationErrorMessage = NegotiationSerializer.deserializeProtocol(jsonNode, ContractNegotiationErrorMessage.class);
				throw new ContractNegotiationAPIException(contractNegotiationErrorMessage, contractNegotiation.getRole() + " did not process Verification message correct");
			} catch (JsonProcessingException e) {
				throw new ContractNegotiationAPIException(contractNegotiation.getRole() + " did not process Verification message correct");
			}
		}
	}
	
	/**
	 * Validate if agreement is valid.
	 * @param agreementId
	 */
	public void validateAgreement(String agreementId) {
		log.info("Validating agreement " + agreementId);
		contractNegotiationRepository.findByAgreement(agreementId)
		.ifPresentOrElse((cn) -> {
			if (!cn.getState().equals(ContractNegotiationState.FINALIZED)) {
				throw new ContractNegotiationAPIException("Contract negotiation with Agreement Id " + agreementId + " is not finalized.");
			}},
				() -> {
					throw new ContractNegotiationAPIException("Contract negotiation with Agreement Id " + agreementId + " not found.");
					});
		// TODO add additional checks like contract dates or else
		//		LocalDateTime agreementStartDate = LocalDateTime.parse(agreement.getTimestamp(), FORMATTER);
		//		agreementStartDate.isBefore(LocalDateTime.now());
		if(!policyEnforcementService.policyEnforcementExists(agreementId)) {
			log.warn("Policy enforcement not created, cannot enforoce properly");
			throw new ContractNegotiationAPIException("Policy enforcement not found for agreement with Id " 
					 + agreementId + " not found.");
		}
	}
	
	private Agreement agreementFromOffer(Offer offer, String assigner) {
		return Agreement.Builder.newInstance()
				.id("urn:uuid:" + UUID.randomUUID().toString())
				.assignee(properties.getAssignee())
				.assigner(assigner)
				.target(offer.getTarget())
				.timestamp(FORMATTER.format(ZonedDateTime.now()))
				.permission(offer.getPermission())
				.build();
	}
	
	private void processContractOffer(Offer offer) {
		offerRepository.findById(offer.getId()).ifPresentOrElse(
				o -> log.info("Offer already exists"), () -> offerRepository.save(offer));
		log.info("PROVIDER - Offer {} saved", offer.getId());
	}
	
	private ContractNegotiation findContractNegotiationByPids (String consumerPid, String providerPid) {
		return contractNegotiationRepository.findByProviderPidAndConsumerPid(providerPid, consumerPid)
				.orElseThrow(() -> new ContractNegotiationAPIException(
						"Contract negotiation with providerPid " + providerPid + 
						" and consumerPid " + consumerPid + " not found"));
	}
    
	private void stateTransitionCheck (ContractNegotiationState newState, ContractNegotiationState currentState) {
		if (!currentState.canTransitTo(newState)) {
			throw new ContractNegotiationAPIException("State transition aborted, " + currentState.name()
					+ " state can not transition to " + newState.name());
		}
	}
    
	private ContractNegotiation findContractNegotiationById (String contractNegotiationId) {
    	return contractNegotiationRepository.findById(contractNegotiationId)
    	        .orElseThrow(() ->
                new ContractNegotiationAPIException("Contract negotiation with id " + contractNegotiationId + " not found"));
    }
}
