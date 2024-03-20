package it.eng.negotiation.transformer.from;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;
import lombok.extern.java.Log;

@Component
@Log
public class JsonFromContractNegotiationTransformer implements TransformInterface<ContractNegotiation, JsonNode> {

	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public Class<ContractNegotiation> getInputType() {
		return ContractNegotiation.class;
	}

	@Override
	public Class<JsonNode> getOutputType() {
		return JsonNode.class;
	}

	@Override
	public JsonNode transform(ContractNegotiation input) {
		Map<String, Object> map = new HashMap<>();
		map.put(DSpaceConstants.CONTEXT, DSpaceConstants.CATALOG_CONTEXT_VALUE);
		map.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + getInputType().getSimpleName());
		map.put(DSpaceConstants.DSPACE_CONSUMER_PID, input.getConsumerPid());
		map.put(DSpaceConstants.DSPACE_PROVIDER_PID, input.getProviderPid());
		try {			
			map.put(DSpaceConstants.DSPACE_STATE, input.getState());  
		} catch (NullPointerException  e) {
			log.warning(e.getMessage() + "\ncaused:\n" + e.getCause());
		}

		return mapper.convertValue(map, JsonNode.class);
	}

}
