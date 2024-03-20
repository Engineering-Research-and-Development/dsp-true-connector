package it.eng.negotiation.transformer.from;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonFromContractRequestMessageTransformer implements TransformInterface<ContractRequestMessage, JsonNode> {

private final ObjectMapper mapper = new ObjectMapper();
		
	@Override
	public Class<ContractRequestMessage> getInputType() {
		return ContractRequestMessage.class;
	}

	@Override
	public Class<JsonNode> getOutputType() {
		return JsonNode.class;
	}

	@Override
	public JsonNode transform(ContractRequestMessage input) {
		Map<String, Object> map = new HashMap<>();
		map.put(DSpaceConstants.CONTEXT, DSpaceConstants.CATALOG_CONTEXT_VALUE);
		map.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + getInputType().getSimpleName());
		map.put(DSpaceConstants.DSPACE_PROVIDER_PID, input.getProviderPid());
		map.put(DSpaceConstants.DSPACE_CONSUMER_PID, input.getConsumerPid());
		map.put(DSpaceConstants.DSPACE_CALLBACK_ADDRESS, input.getCallbackAddress());
				
		return mapper.convertValue(map, JsonNode.class);
	}

}
