package it.eng.negotiation.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.rest.protocol.ContractNegotiationCallback;
import it.eng.negotiation.serializer.Serializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponseEvent;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationEventHandlerService {
	
	private final ContractNegotiationRepository contractNegotiationRepository;
	private final AgreementRepository agreementRepository;
	private final ContractNegotiationProperties properties;
	private final OkHttpRestClient okHttpRestClient;
	
	public ContractNegotiationEventHandlerService(ContractNegotiationRepository contractNegotiationRepository,
			ContractNegotiationProperties properties, OkHttpRestClient okHttpRestClient, AgreementRepository agreementRepository) {
		this.contractNegotiationRepository = contractNegotiationRepository;
		this.agreementRepository = agreementRepository;
		this.properties = properties;
		this.okHttpRestClient = okHttpRestClient;
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
					.callbackAddress(properties.callbackAddress())
					.agreement(agreementFromOffer(Serializer.deserializePlain(offerResponse.getOffer().toPrettyString(), Offer.class)))
					.build();
			
			String authorization = okhttp3.Credentials.basic("connector@mail.com", "password");
			GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(
					contractNegotiation.getCallbackAddress() + ContractNegotiationCallback.getContractAgreementCallback("consumer", contractNegotiation.getConsumerPid()), 
					Serializer.serializeProtocolJsonNode(agreementMessage),
					authorization);
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

	private Agreement agreementFromOffer(Offer offer) {
		return Agreement.Builder.newInstance()
				.id(UUID.randomUUID().toString())
				.assignee(properties.callbackAddress())
				.assigner(properties.callbackAddress())
				.target("target")
				.timestamp("now")
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

		String authorization = okhttp3.Credentials.basic("connector@mail.com", "password");
		String callbackAddress = ContractNegotiationCallback.getProviderAgreementVerificationCallback(contractNegotiation.getCallbackAddress(), verificationMessage.getProviderPid());
		log.info("Sending verification message to provider to {}", callbackAddress);
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(callbackAddress, 
				Serializer.serializeProtocolJsonNode(verificationMessage),
				authorization);
		
		if(response.getHttpStatus() == 200) {
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
