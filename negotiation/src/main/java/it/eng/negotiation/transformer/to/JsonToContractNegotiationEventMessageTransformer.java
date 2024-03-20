package it.eng.negotiation.transformer.to;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonToContractNegotiationEventMessageTransformer implements TransformInterface<JsonNode, ContractNegotiationEventMessage> { 

	@Override
	public Class<JsonNode> getInputType() {
		return JsonNode.class;
	}
	@Override
	public Class<ContractNegotiationEventMessage> getOutputType() {
		return ContractNegotiationEventMessage.class;
	}

	public ContractNegotiationEventMessage transform(JsonNode input) {
		var builder = ContractNegotiationEventMessage.Builder.newInstance();

		builder.consumerPid(input.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		builder.providerPid(input.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());

		JsonNode eventType = input.get(DSpaceConstants.DSPACE_EVENT_TYPE);
		if(eventType != null) {
			builder.eventType(eventType.asText());		
		}
				
		return builder.build();
	}

}
