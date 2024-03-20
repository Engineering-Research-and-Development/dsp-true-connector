package it.eng.catalog.transformer.to;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonToCatalogRequestMessageTransform implements TransformInterface<JsonNode, CatalogRequestMessage> {

	@Override
	public Class<CatalogRequestMessage> getOutputType() {
		return CatalogRequestMessage.class;
	}
	@Override
	public Class<JsonNode> getInputType() {
		return JsonNode.class;
	}

	@Override
	public CatalogRequestMessage transform(JsonNode input) {
		var builder = CatalogRequestMessage.Builder.newInstance();
		if(!ObjectUtils.isEmpty(input.get(DSpaceConstants.FILTER))) {
			List<String> filters = new ArrayList<>();
			input.get(DSpaceConstants.FILTER).forEach(f -> filters.add(f.asText()));
			builder.filter(filters);
		}
		return builder.build();
	}



}
