package it.eng.negotiation.transformer.to;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.Reason;
import it.eng.negotiation.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

public class JsonToReasonTrasformer implements TransformInterface<JsonNode, Reason> {

	@Override
	public Class<JsonNode> getInputType() {
		return JsonNode.class;
	}

	@Override
	public Class<Reason> getOutputType() {
		return Reason.class;
	}

	@Override
	public Reason transform(JsonNode input) {
		var builder = Reason.Builder.newInstance(); 
		
		JsonNode value = input.get(DSpaceConstants.VALUE);
		JsonNode language = input.get(DSpaceConstants.LANGUAGE);
		
		if(value != null) {
			builder.value(null);
		}
		if(language != null) {
			builder.language(null);
		}
		
		return builder.build();
	}

}
