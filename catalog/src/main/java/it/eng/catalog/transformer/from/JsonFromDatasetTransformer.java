package it.eng.catalog.transformer.from;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonFromDatasetTransformer implements TransformInterface<Dataset, JsonNode>{
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	@Override
	public Class<Dataset> getInputType() {
		return Dataset.class;
	}

	@Override
	public Class<JsonNode> getOutputType() {
		return JsonNode.class;
	}

	@Override
	public JsonNode transform(Dataset input) {
		Map<String, Object> map = new HashMap<>();
		map.put(DSpaceConstants.CONTEXT, DSpaceConstants.DATASET_CONTEXT_VALUE);
		map.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + getInputType().getSimpleName());
		map.put(DSpaceConstants.ID, input.getId());
		map.put(DSpaceConstants.KEYWORD, input.getKeyword());
		List<Object> distributions = new ArrayList<>();
		JsonFromDistributionTransform jsonFromDistributionTransform = new JsonFromDistributionTransform();
		for (Distribution distribution:input.getDistribution()) {
			distributions.add(jsonFromDistributionTransform.transform(distribution));
		}
		map.put(DSpaceConstants.DISTRIBUTION, distributions);
//		List<Object> offers = new ArrayList<>();
//		JsonFromPolicyTransformer jsonFromPolicyTransformer = new JsonFromPolicyTransformer();
		// TODO offer
//		for (Entry<String, Policy> policy : input.getOffers().entrySet()) {
//			offers.add(jsonFromPolicyTransformer.transform(policy.getValue()));
//		}
		return mapper.convertValue(map, JsonNode.class);
	}

}
