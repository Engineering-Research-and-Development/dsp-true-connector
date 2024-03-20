package it.eng.catalog.transformer.from;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

public class JsonFromCatalogRequestMessage implements TransformInterface<CatalogRequestMessage, JsonNode>{

	ObjectMapper mapper = new ObjectMapper();
	
	@Override
	public Class<CatalogRequestMessage> getInputType() {
		return CatalogRequestMessage.class;
	}

	@Override
	public Class<JsonNode> getOutputType() {
		return JsonNode.class;
	}

	@Override
	public JsonNode transform(CatalogRequestMessage input) {
		Map<String, Object> map = new HashMap<>();
		map.put(DSpaceConstants.CONTEXT, DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE);
		map.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + CatalogRequestMessage.class.getSimpleName());
		map.put(DSpaceConstants.FILTER, input.getFilter());
		
		return mapper.convertValue(map, JsonNode.class);
	}

}
