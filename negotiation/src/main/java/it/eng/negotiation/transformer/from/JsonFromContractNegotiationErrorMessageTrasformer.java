package it.eng.negotiation.transformer.from;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonFromContractNegotiationErrorMessageTrasformer
		implements TransformInterface<ContractNegotiationErrorMessage, JsonNode> {

	private final ObjectMapper mapper = new ObjectMapper();
	
	@Override
	public Class<ContractNegotiationErrorMessage> getInputType() {
		return ContractNegotiationErrorMessage.class;
	}

	@Override
	public Class<JsonNode> getOutputType() {
		return JsonNode.class;
	}

	@Override
	public JsonNode transform(ContractNegotiationErrorMessage input) {
		Map<String, Object> map = new HashMap<>();
		map.put(DSpaceConstants.CONTEXT, DSpaceConstants.CATALOG_CONTEXT_VALUE);
		map.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + getInputType().getSimpleName());
		map.put(DSpaceConstants.DSPACE_CONSUMER_PID, input.getConsumerPid());
		map.put(DSpaceConstants.DSPACE_PROVIDER_PID, input.getProviderPid());
		map.put(DSpaceConstants.DSPACE_CODE, input.getCode());
		
		// List reason
		
		//JsonFromReasonTrasformer.transform();
		List<Map<String, String>> reasons = new ArrayList<>();
		input.getReason().forEach(reason -> {
			Map<String, String> r = new HashMap<>();
			r.put(DSpaceConstants.VALUE, reason.getValue());
			r.put(DSpaceConstants.LANGUAGE, reason.getLanguage());
			reasons.add(r);
		});
		map.put(DSpaceConstants.DSPACE_REASON, reasons);
				
		//List description
		List<Map<String, String>> descriptions = new ArrayList<>();
		input.getDescription().forEach(description -> {
			Map<String, String> r = new HashMap<>();
			r.put(DSpaceConstants.VALUE, description.getValue());
			r.put(DSpaceConstants.LANGUAGE, description.getLanguage());
			descriptions.add(r);
		});
		map.put(DSpaceConstants.DCT_DESCRIPTION, descriptions);
				
		return mapper.convertValue(map, JsonNode.class);
	}

}
