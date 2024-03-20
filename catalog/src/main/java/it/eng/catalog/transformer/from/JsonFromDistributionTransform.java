package it.eng.catalog.transformer.from;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.catalog.model.Distribution;
import it.eng.catalog.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonFromDistributionTransform implements TransformInterface<Distribution, JsonNode> {
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	@Override
	public Class<Distribution> getInputType() {
		return Distribution.class;
	}

	@Override
	public Class<JsonNode> getOutputType() {
		return JsonNode.class;
	}

	@Override
	public JsonNode transform(Distribution input) {
		Map<String, Object> map = new HashMap<>();
		map.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + getInputType().getSimpleName());
		map.put(DSpaceConstants.DCT_FORMAT, input.getFormat());
		map.put(DSpaceConstants.ACCESS_SERVICE, input.getDataservice());
		JsonFromDataServiceTransform jsonFromDataServiceTransform = new JsonFromDataServiceTransform();
		// TODO make sure that it has one element and cover if there are more
		map.put(DSpaceConstants.DATA_SERVICE, jsonFromDataServiceTransform.transform(input.getDataservice().get(0)));
		return mapper.convertValue(map, JsonNode.class);
	}

}
