package it.eng.negotiation.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.properties.ContractNegotiationProperties;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.rest.protocol.ContactNegotiationCallback;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ContractNegotiationEventHandlerService {
	
	@Autowired
    private ContractNegotiationRepository repository;
	@Autowired
	private ContractNegotiationProperties properties;
	
	@Autowired
	private CallbackHandler callbackHandler;
	
	public void handleContractNegotiationOfferResponse(ContractNegotiationOfferResponse response) {
		String result = response.isOfferAccepted() ? "accepted" : "declined";
		log.info("Contract offer " + result);
		// TODO get callbackAddress and send Agreement message
		log.info("ConsumerPid - " + response.getConsumerPid() + ", providerPid - " + response.getProviderPid());
		Optional<ContractNegotiation> contractNegtiationOpt = repository.findByProviderPidAndConsumerPid(response.getProviderPid(), response.getConsumerPid());
		contractNegtiationOpt.ifPresent(cn -> log.info("Found intial negotiation" + " - CallbackAddress " + cn.getCallbackAddress()));
		ContractNegotiation contractNegotiation = contractNegtiationOpt.get();
		if(response.isOfferAccepted()) {
			ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
					.consumerPid(contractNegotiation.getConsumerPid())
					.providerPid(contractNegotiation.getProviderPid())
					.callbackAddress(properties.callbackAddress())
					.agreement(agreementFromOffer(Serializer.deserializeProtocol(response.getOffer(), Offer.class)))
					.build();
			
			int status = callbackHandler.handleCallbackResponseProtocol(
					contractNegotiation.getCallbackAddress() + ContactNegotiationCallback.getContractAgreementCallback("consumer", contractNegotiation.getConsumerPid()), 
					Serializer.serializeProtocolJsonNode(agreementMessage));
			if(status == 200) {
				log.info("Updating status for negotiation {} to agreed", contractNegotiation.getId());
				ContractNegotiation contractNegtiationUpdate = ContractNegotiation.Builder.newInstance()
						.id(contractNegotiation.getId())
						.callbackAddress(contractNegotiation.getCallbackAddress())
						.consumerPid(contractNegotiation.getConsumerPid())
						.providerPid(contractNegotiation.getProviderPid())
						.state(ContractNegotiationState.AGREED)
						.callbackAddress(contractNegotiation.getCallbackAddress())
						.build();
				repository.save(contractNegtiationUpdate);
				// TODO save agreement also
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
				repository.findByProviderPidAndConsumerPid(verificationMessage.getProviderPid(), verificationMessage.getConsumerPid());
		contractNegtiationOpt.ifPresent(cn -> log.info("Found intial negotiation" + " - CallbackAddress " + cn.getCallbackAddress()));
		ContractNegotiation contractNegtiation = contractNegtiationOpt.get();

		String callbackAddress = contractNegtiation.getCallbackAddress() + ContactNegotiationCallback.getProviderHandleAgreementCallback(verificationMessage.getProviderPid());
		log.info("Sending verification message to provider to {}", callbackAddress);
		int status = callbackHandler.handleCallbackResponseProtocol(callbackAddress, Serializer.serializeProtocolJsonNode(verificationMessage));
		
		if(status == 200) {
			log.info("Updating status for negotiation {} to verified", contractNegtiation.getId());
			ContractNegotiation contractNegtiationUpdate = ContractNegotiation.Builder.newInstance()
					.id(contractNegtiation.getId())
					.callbackAddress(contractNegtiation.getCallbackAddress())
					.consumerPid(contractNegtiation.getConsumerPid())
					.providerPid(contractNegtiation.getProviderPid())
					.state(ContractNegotiationState.FINALIZED)
					.callbackAddress(contractNegtiation.getCallbackAddress())
					.build();
			repository.save(contractNegtiationUpdate);
		} else {
			log.info("Response status not 200 - consumer did not process Verification message correct - what to do with it???");
		}
	}

}
