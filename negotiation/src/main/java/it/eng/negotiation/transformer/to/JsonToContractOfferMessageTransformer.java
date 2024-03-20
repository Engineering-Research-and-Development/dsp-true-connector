package it.eng.negotiation.transformer.to;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractOfferMessage;
import it.eng.negotiation.model.Offer;
import it.eng.negotiation.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

public class JsonToContractOfferMessageTransformer implements TransformInterface<JsonNode, ContractOfferMessage> {

	@Override
	public Class<JsonNode> getInputType() {
		return JsonNode.class;
	}

	@Override
	public Class<ContractOfferMessage> getOutputType() {
		return ContractOfferMessage.class;
	}

	@Override
	public ContractOfferMessage transform(JsonNode input) {
		var builder = ContractOfferMessage.Builder.newInstance();
		
		builder.providerPid(input.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		JsonNode consumerId = input.get(DSpaceConstants.DSPACE_CONSUMER_PID);
		if(consumerId != null) {
			builder.consumerPid(consumerId.asText());
		}
		JsonNode callbackAddress = input.get(DSpaceConstants.DSPACE_CALLBACK_ADDRESS);
		if(callbackAddress != null) {
			builder.callbackAddress(callbackAddress.asText());
		}

		JsonNode offerJson = input.get(DSpaceConstants.DSPACE_OFFER);
		JsonToOfferTransformer offerTransformer = new JsonToOfferTransformer();
		Offer offer = offerTransformer.transform(offerJson);
		builder.offer(offer);
		return builder.build();
	}

}
