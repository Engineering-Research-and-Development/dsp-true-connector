package it.eng.negotiation.transformer.to;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.negotiation.model.Reason;
import it.eng.negotiation.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

public class JsonToContractNegotiationTerminationMessageTransformer implements TransformInterface<JsonNode, ContractNegotiationTerminationMessage> {

	@Override
	public Class<JsonNode> getInputType() {
		return JsonNode.class;
	}

	@Override
	public Class<ContractNegotiationTerminationMessage> getOutputType() {
		return ContractNegotiationTerminationMessage.class;
	}

	@Override
	public ContractNegotiationTerminationMessage transform(JsonNode input) {
		var builder = ContractNegotiationTerminationMessage.Builder.newInstance();

		JsonNode code = input.get(DSpaceConstants.DSPACE + DSpaceConstants.PROCESS_ID);
		builder.consumerPid(input.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		builder.providerPid(input.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());

		if(code != null) {
			builder.code(code.asText());
		}

		JsonNode reasonsJson = input.get(DSpaceConstants.DSPACE_REASON);
		if(reasonsJson instanceof ArrayNode) {
			List<Reason> reasons = new ArrayList<>();
			for(JsonNode reas: ((ArrayNode) reasonsJson)) {
				reasons.add(Reason.Builder.newInstance()
						.language(reas.get(DSpaceConstants.LANGUAGE).asText())
						.value(reas.get(DSpaceConstants.VALUE).asText())
						.build());
			}
			builder.reason(reasons);
		} else {
			builder.reason(Arrays.asList(
					Reason.Builder.newInstance()
					.language(reasonsJson.get(DSpaceConstants.LANGUAGE).asText())
					.value(reasonsJson.get(DSpaceConstants.VALUE).asText())
					.build()));
		}
		return builder.build();
	}
}
