package it.eng.catalog.transformer.to;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.transformer.TransformInterface;

public class JsonToCatalogTransform implements TransformInterface<JsonNode, Catalog>{

	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public Class<JsonNode> getInputType() {
		return JsonNode.class;
	}

	@Override
	public Class<Catalog> getOutputType() {
		return Catalog.class;
	}

	@Override
	public Catalog transform(JsonNode input) {
		return null;
	}

}
