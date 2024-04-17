package it.eng.negotiation.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.rest.protocol.ContactNegotiationCallback;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.event.contractnegotiation.ContractNegotiationOfferResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Service
@Slf4j
public class ContractNegotiationEventHandlerService {
	
	@Autowired
    private ContractNegotiationRepository repository;
	
	@Autowired
	private CallbackHandler callbackHandler;
	
	public void handleContractNegotiationOfferResponse(ContractNegotiationOfferResponse response) {
		String result = response.isOfferAccepted() ? "accepted" : "declined";
		log.info("Contract offer " + result);
		// TODO get callbackAddress and send Agreement message
		log.info("ConsumerPid - " + response.getConsumerPid() + ", providerPid - " + response.getProviderPid());
		Optional<ContractNegotiation> contractNegtiationOpt = repository.findByProviderPidAndConsumerPid(response.getProviderPid(), response.getConsumerPid());
		contractNegtiationOpt.ifPresent(cn -> log.info("Found intial negotiation" + " - CallbackAddress " + cn.getCallbackAddress()));
		ContractNegotiation contractNegtiation = contractNegtiationOpt.get();
		if(response.isOfferAccepted()) {
			ContractAgreementMessage agreementMessage = ContractAgreementMessage.Builder.newInstance()
					.callbackAddress(getCallbackAddress())
					.consumerPid(contractNegtiation.getConsumerPid())
					.providerPid(contractNegtiation.getProviderPid())
					.agreement(agreementFromOffer(Serializer.deserializeProtocol(response.getOffer(), Offer.class)))
					.build();
			
//			RequestBody body = RequestBody.create(Serializer.serializeProtocol(agreementMessage),
//					MediaType.parse("application/json"));
//
//			Request request = new Request.Builder().url(contractNegtiation.getCallbackAddress() + 
//					ContactNegotiationCallback.getContractAgreementCallback("consumer", contractNegtiation.getConsumerPid()))
//					.post(body)
//					.build();
//
//			Response okHttpResponse = okHttpClient.executeCall(request);
//			log.info("Response received, status {}", okHttpResponse.code());
			callbackHandler.handleCallbackResponseProtocol(
					ContactNegotiationCallback.getContractAgreementCallback("consumer", contractNegtiation.getConsumerPid()), 
					Serializer.serializeProtocolJsonNode(agreementMessage));
		}
	}

	private Agreement agreementFromOffer(Offer offer) {
		return Agreement.Builder.newInstance()
				.id(UUID.randomUUID().toString())
				.assignee(getCallbackAddress())
				.assigner(getCallbackAddress())
				.target("target")
				.timestamp("now")
				.permission(offer.getPermission())
				.build();
	}

	private String getCallbackAddress() {
		return "abc";
	}
}
