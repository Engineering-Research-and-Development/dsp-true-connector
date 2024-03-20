package it.eng.catalog.transformer.from;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.catalog.model.DataService;
import it.eng.catalog.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonFromDataServiceTransform implements TransformInterface<DataService, JsonNode> {
	
	ObjectMapper mapper = new ObjectMapper();

	@Override
	public Class<DataService> getInputType() {
		return DataService.class;
	}

	@Override
	public Class<JsonNode> getOutputType() {
		return JsonNode.class;
	}

	@Override
	public JsonNode transform(DataService input) {
		Map<String, String> map = new HashMap<>();
		map.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + getInputType().getSimpleName());
		map.put(DSpaceConstants.ID, input.getId());
//		map.put(DSpaceConstants.DCT_TERMS, input.getTerms());
		map.put(DSpaceConstants.DCAT_ENDPOINT_URL, input.getEndpointURL());
		return mapper.convertValue(map, JsonNode.class);
	}

}
