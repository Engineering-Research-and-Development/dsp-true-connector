package it.eng.negotiation.service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
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
import it.eng.negotiation.rest.protocol.ContractNegotiationCallback;
import it.eng.negotiation.serializer.Serializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponseEvent;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationEventHandlerService {
	
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
	
	private final ContractNegotiationRepository contractNegotiationRepository;
	private final AgreementRepository agreementRepository;
	private final ContractNegotiationProperties properties;
	private final OkHttpRestClient okHttpRestClient;
	private final CredentialUtils credentialUtils;
	
	public ContractNegotiationEventHandlerService(ContractNegotiationRepository contractNegotiationRepository,
			ContractNegotiationProperties properties, OkHttpRestClient okHttpRestClient,
			AgreementRepository agreementRepository, CredentialUtils credentialUtils) {
		this.contractNegotiationRepository = contractNegotiationRepository;
		this.agreementRepository = agreementRepository;
		this.properties = properties;
		this.okHttpRestClient = okHttpRestClient;
		this.credentialUtils = credentialUtils;
	}

	public void handleContractNegotiationOfferResponse(ContractNegotiationOfferResponseEvent offerResponse) {
		String result = offerResponse.isOfferAccepted() ? "accepted" : "declined";
		log.info("Contract offer " + result);
		// TODO get callbackAddress and send Agreement message
		log.info("ConsumerPid - " + offerResponse.getConsumerPid() + ", providerPid - " + offerResponse.getProviderPid());
		Optional<ContractNegotiation> contractNegtiationOpt = contractNegotiationRepository.findByProviderPidAndConsumerPid(offerResponse.getProviderPid(), offerResponse.getConsumerPid());
		contractNegtiationOpt.ifPresent(cn -> log.info("Found intial negotiation" + " - CallbackAddress " + cn.getCallbackAddress()));
		ContractNegotiation contractNegotiation = contractNegtiationOpt.get();
		if(offerResponse.isOfferAccepted()) {
			ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
					.consumerPid(contractNegotiation.getConsumerPid())
					.providerPid(contractNegotiation.getProviderPid())
					.callbackAddress(properties.providerCallbackAddress())
					.agreement(agreementFromOffer(Serializer.deserializePlain(offerResponse.getOffer().toPrettyString(), Offer.class), contractNegotiation.getAssigner()))
					.build();
			
			GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
					ContractNegotiationCallback.getContractAgreementCallback(contractNegotiation.getCallbackAddress(), contractNegotiation.getConsumerPid()), 
					Serializer.serializeProtocolJsonNode(agreementMessage),
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
		ContractNegotiation contractNegotiation = contractNegotiationRepository.findById(contractNegotiationId)
    		.orElseThrow(() ->
    			new ContractNegotiationNotFoundException("Contract negotiation with id " + contractNegotiationId + " not found"));
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
				Serializer.serializeProtocolJsonNode(negotiationTerminatedEventMessage),
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

	public void verifyNegotiation(ContractAgreementVerificationMessage verificationMessage) {
		log.info("ConsumerPid - " + verificationMessage.getConsumerPid() + ", providerPid - " + verificationMessage.getProviderPid());
		ContractNegotiation contractNegotiation =  contractNegotiationRepository
				.findByProviderPidAndConsumerPid(verificationMessage.getProviderPid(), verificationMessage.getConsumerPid())
				.orElseThrow(() -> new ContractNegotiationAPIException(
						"Contract negotiation with providerPid " + verificationMessage.getProviderPid() + 
						" and consumerPid " + verificationMessage.getConsumerPid() + " not found"));
		
		if (!contractNegotiation.getState().equals(ContractNegotiationState.AGREED)) {
			throw new ContractNegotiationAPIException("Agreement aborted, wrong state " + contractNegotiation.getState().name());
		}
		log.info("Found intial negotiation" + " - CallbackAddress " + contractNegotiation.getCallbackAddress());

		String callbackAddress = ContractNegotiationCallback.getProviderAgreementVerificationCallback(contractNegotiation.getCallbackAddress(), verificationMessage.getProviderPid());
		log.info("Sending verification message to provider to {}", callbackAddress);
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(callbackAddress, 
				Serializer.serializeProtocolJsonNode(verificationMessage),
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

}
