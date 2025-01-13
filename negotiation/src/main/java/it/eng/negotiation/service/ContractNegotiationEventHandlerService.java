package it.eng.negotiation.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.stereotype.Service;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.listener.ContractNegotiationPublisher;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Reason;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.repository.OfferRepository;
import it.eng.negotiation.rest.protocol.ContractNegotiationCallback;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.policy.PolicyManager;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponseEvent;
import it.eng.tools.event.policyenforcement.ArtifactConsumedEvent;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationEventHandlerService extends BaseProtocolService {
	
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
	
	private final AgreementRepository agreementRepository;
	protected final CredentialUtils credentialUtils;
	private final PolicyManager policyManager;
	
	public ContractNegotiationEventHandlerService(ContractNegotiationPublisher publisher,
			ContractNegotiationRepository contractNegotiationRepository, OkHttpRestClient okHttpRestClient,
			ContractNegotiationProperties properties, OfferRepository offerRepository,
			AgreementRepository agreementRepository, CredentialUtils credentialUtils,
			PolicyManager policyManager) {
		super(publisher, contractNegotiationRepository, okHttpRestClient, properties, offerRepository);
		this.agreementRepository = agreementRepository;
		this.credentialUtils = credentialUtils;
		this.policyManager = policyManager;
	}

	@Deprecated
	public void handleContractNegotiationOfferResponse(ContractNegotiationOfferResponseEvent offerResponse) {
		String result = offerResponse.isOfferAccepted() ? "accepted" : "declined";
		log.info("Contract offer " + result);
		// TODO get callbackAddress and send Agreement message
		log.info("ConsumerPid - " + offerResponse.getConsumerPid() + ", providerPid - " + offerResponse.getProviderPid());
		ContractNegotiation contractNegotiation = findContractNegotiationByPids(offerResponse.getConsumerPid(), offerResponse.getProviderPid());
		log.info("Found intial negotiation" + " - CallbackAddress " + contractNegotiation.getCallbackAddress());
		if(offerResponse.isOfferAccepted()) {
			ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
					.consumerPid(contractNegotiation.getConsumerPid())
					.providerPid(contractNegotiation.getProviderPid())
					.callbackAddress(properties.providerCallbackAddress())
					.agreement(agreementFromOffer(NegotiationSerializer.deserializePlain(offerResponse.getOffer().toPrettyString(), Offer.class), contractNegotiation.getAssigner()))
					.build();
			
			GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
					ContractNegotiationCallback.getContractAgreementCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getConsumerPid()), 
					NegotiationSerializer.serializeProtocolJsonNode(agreementMessage),
					credentialUtils.getConnectorCredentials());
			if(response.isSuccess()) {
				log.info("Updating status for negotiation {} to agreed", contractNegotiation.getId());
				ContractNegotiation contractNegtiationUpdate = ContractNegotiation.Builder.newInstance()
						.id(contractNegotiation.getId())
						.callbackAddress(contractNegotiation.getCallbackAddress())
						.consumerPid(contractNegotiation.getConsumerPid())
						.providerPid(contractNegotiation.getProviderPid())
						.state(ContractNegotiationState.AGREED)
						.callbackAddress(contractNegotiation.getCallbackAddress())
						.build();
				contractNegotiationRepository.save(contractNegtiationUpdate);
				log.info("Saving agreement..." + agreementMessage.getAgreement().getId());
				agreementRepository.save(agreementMessage.getAgreement());
			} else {
				log.error("Response status not 200 - consumer did not process AgreementMessage correct");
				throw new ContractNegotiationAPIException("consumer did not process AgreementMessage correct");
			}
		}
	}
	
	public ContractNegotiation handleContractNegotiationTerminated(String contractNegotiationId) {
		ContractNegotiation contractNegotiation = findContractNegotiationById(contractNegotiationId);
		// for now just log it; maybe we can publish event?
		log.info("Contract negotiation with consumerPid {} and providerPid {} declined", contractNegotiation.getConsumerPid(), contractNegotiation.getProviderPid());
		ContractNegotiationTerminationMessage negotiationTerminatedEventMessage = ContractNegotiationTerminationMessage.Builder.newInstance()
			.consumerPid(contractNegotiation.getConsumerPid())
			.providerPid(contractNegotiation.getProviderPid())
			.code(contractNegotiationId)
			.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("Contract negotiation terminated by provider").build()))
			.build();
			
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
				ContractNegotiationCallback.getContractTerminationCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getConsumerPid()), 
				NegotiationSerializer.serializeProtocolJsonNode(negotiationTerminatedEventMessage),
				credentialUtils.getConnectorCredentials());
		if(response.isSuccess()) {
			log.info("Updating status for negotiation {} to terminated", contractNegotiation.getId());
			ContractNegotiation contractNegtiationTerminated = contractNegotiation.withNewContractNegotiationState(ContractNegotiationState.TERMINATED);
			contractNegotiationRepository.save(contractNegtiationTerminated);
			return contractNegtiationTerminated;
		} else {
			log.error("Response status not 200 - consumer did not process AgreementMessage correct");
			throw new ContractNegotiationAPIException("consumer did not process AgreementMessage correct");
		}
	}

	private Agreement agreementFromOffer(Offer offer, String assigner) {
		return Agreement.Builder.newInstance()
				.id(UUID.randomUUID().toString())
				.assignee(properties.getAssignee())
				.assigner(assigner)
				.target(offer.getTarget())
				.timestamp(FORMATTER.format(ZonedDateTime.now()))
				.permission(offer.getPermission())
				.build();
	}

	public void verifyNegotiation(String consumerPid, String providerPid) {
		log.info("ConsumerPid - " + consumerPid + ", providerPid - " + providerPid);
		ContractNegotiation contractNegotiation =  findContractNegotiationByPids(consumerPid, providerPid);
		
		stateTransitionCheck(ContractNegotiationState.VERIFIED, contractNegotiation);
		
		ContractAgreementVerificationMessage verificationMessage = ContractAgreementVerificationMessage.Builder.newInstance()
				.consumerPid(consumerPid)
				.providerPid(providerPid)
				.build();
		
		log.info("Found intial negotiation" + " - CallbackAddress " + contractNegotiation.getCallbackAddress());

		String callbackAddress = ContractNegotiationCallback.getProviderAgreementVerificationCallback(contractNegotiation.getCallbackAddress(), providerPid);
		log.info("Sending verification message to provider to {}", callbackAddress);
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(callbackAddress, 
				NegotiationSerializer.serializeProtocolJsonNode(verificationMessage),
				credentialUtils.getConnectorCredentials());
		
		if(response.isSuccess()) {
			log.info("Updating status for negotiation {} to verified", contractNegotiation.getId());
			ContractNegotiation contractNegtiationUpdate = ContractNegotiation.Builder.newInstance()
					.id(contractNegotiation.getId())
					.callbackAddress(contractNegotiation.getCallbackAddress())
					.consumerPid(contractNegotiation.getConsumerPid())
					.providerPid(contractNegotiation.getProviderPid())
					.state(ContractNegotiationState.VERIFIED)
					.build();
			contractNegotiationRepository.save(contractNegtiationUpdate);
		} else {
			log.error("Response status not 200 - provider did not process Verification message correct");
			throw new ContractNegotiationAPIException("provider did not process Verification message correct");
		}
	}

	public void artifactConsumedEvent(ArtifactConsumedEvent artifactConsumedEvent) {
		log.info("Increasing access count for artifactId {}", artifactConsumedEvent.getAgreementId());
		policyManager.updateAccessCount(artifactConsumedEvent.getAgreementId());
	}
}
