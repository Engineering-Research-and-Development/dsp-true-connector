package it.eng.negotiation.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.AgreementRepository;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.rest.protocol.ContractNegotiationCallback;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.contractnegotiation.OfferValidationResponse;
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

	public void handleContractNegotiationOfferResponse(OfferValidationResponse offerResponse) {
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
				log.info("Response status not 200 - consumer did not process AgreementMessage correct - what to do with it???");
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

	public void contractAgreementVerificationMessage(ContractAgreementVerificationMessage verificationMessage) {
		log.info("ConsumerPid - " + verificationMessage.getConsumerPid() + ", providerPid - " + verificationMessage.getProviderPid());
		Optional<ContractNegotiation> contractNegtiationOpt = 
				contractNegotiationRepository.findByProviderPidAndConsumerPid(verificationMessage.getProviderPid(), verificationMessage.getConsumerPid());
		contractNegtiationOpt.ifPresent(cn -> log.info("Found intial negotiation" + " - CallbackAddress " + cn.getCallbackAddress()));
		ContractNegotiation contractNegtiation = contractNegtiationOpt.get();

		String authorization = okhttp3.Credentials.basic("connector@mail.com", "password");
		String callbackAddress = contractNegtiation.getCallbackAddress() + ContractNegotiationCallback.getProviderHandleAgreementCallback(verificationMessage.getProviderPid());
		log.info("Sending verification message to provider to {}", callbackAddress);
		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(callbackAddress, 
				Serializer.serializeProtocolJsonNode(verificationMessage),
				authorization);
		
		if(response.getHttpStatus() == 200) {
			log.info("Updating status for negotiation {} to verified", contractNegtiation.getId());
			ContractNegotiation contractNegtiationUpdate = ContractNegotiation.Builder.newInstance()
					.id(contractNegtiation.getId())
					.callbackAddress(contractNegtiation.getCallbackAddress())
					.consumerPid(contractNegtiation.getConsumerPid())
					.providerPid(contractNegtiation.getProviderPid())
					.state(ContractNegotiationState.FINALIZED)
					.callbackAddress(contractNegtiation.getCallbackAddress())
					.build();
			contractNegotiationRepository.save(contractNegtiationUpdate);
		} else {
			log.info("Response status not 200 - consumer did not process Verification message correct - what to do with it???");
		}
	}

}
