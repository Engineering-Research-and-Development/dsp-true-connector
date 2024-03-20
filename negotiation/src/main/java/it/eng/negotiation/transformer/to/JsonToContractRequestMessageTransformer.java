package it.eng.negotiation.transformer.to;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonToContractRequestMessageTransformer implements TransformInterface<JsonNode, ContractRequestMessage> {

	@Override
	public Class<ContractRequestMessage> getOutputType() {
		return ContractRequestMessage.class;
	}
	@Override
	public Class<JsonNode> getInputType() {
		return JsonNode.class;
	}

	@Override
	public ContractRequestMessage transform(JsonNode input) {
		var builder = ContractRequestMessage.Builder.newInstance();
		
		JsonNode dataset = input.get(DSpaceConstants.DSPACE_DATASET);
		JsonNode providerPid = input.get(DSpaceConstants.DSPACE_PROVIDER_PID);
		JsonNode consumerPid = input.get(DSpaceConstants.DSPACE_CONSUMER_PID);
		JsonNode callbackAddress = input.get(DSpaceConstants.DSPACE_CALLBACK_ADDRESS);

		if(providerPid != null) {
			builder.providerPid(providerPid.asText());
		}
		if(consumerPid != null) {
			builder.consumerPid(consumerPid.asText());
		}
		if(callbackAddress != null) {
			builder.callbackAddress(callbackAddress.asText());
		}
		
		JsonNode offerJson = input.get(DSpaceConstants.DSPACE_OFFER);
		if(offerJson != null) {
			JsonToOfferTransformer offerTransformer = new JsonToOfferTransformer(); 
			Offer offer = offerTransformer.transform(offerJson);
			builder.offer(offer);		
		}
				
		return builder.build();
	}



}
