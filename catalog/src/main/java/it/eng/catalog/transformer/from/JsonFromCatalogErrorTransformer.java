package it.eng.catalog.transformer.from;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.catalog.model.CatalogError;
import it.eng.catalog.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonFromCatalogErrorTransformer implements TransformInterface <CatalogError, JsonNode>{

	ObjectMapper mapper = new ObjectMapper();
	
	@Override
	public Class<CatalogError> getInputType() {
		return CatalogError.class;
	}

	@Override
	public Class<JsonNode> getOutputType() {
		return JsonNode.class;
	}

	@Override
	public JsonNode transform(CatalogError input) {
		Map<String, Object> map = new HashMap<>();
		map.put(DSpaceConstants.CONTEXT, DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE);
		map.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + CatalogError.class.getSimpleName());
		map.put(DSpaceConstants.CODE, input.getCode());
		List<Map<String, String>> reasons = new ArrayList<>();
		input.getReason().forEach(reason -> {
			Map<String, String> r = new HashMap<>();
			r.put(DSpaceConstants.VALUE, reason.getValue());
			r.put(DSpaceConstants.LANGUAGE, reason.getLanguage());
			reasons.add(r);
		});
		map.put(DSpaceConstants.DSPACE_REASON, reasons);
		map.put(DSpaceConstants.REASON, reasons);
		return mapper.convertValue(map, JsonNode.class);
	}

}
