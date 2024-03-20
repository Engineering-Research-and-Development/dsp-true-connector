package it.eng.negotiation.transformer.to;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.Offer;
import it.eng.negotiation.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonToOfferTransformer implements TransformInterface<JsonNode, Offer> {

	@Override
	public Class<Offer> getOutputType() {
		return Offer.class;
	}
	@Override
	public Class<JsonNode> getInputType() {
		return JsonNode.class;
	}

	@Override
	public Offer transform(JsonNode input) {
		var builder = Offer.Builder.newInstance();
		
		JsonNode id = input.get(DSpaceConstants.ID);
		JsonNode target = input.get(DSpaceConstants.ODRL + DSpaceConstants.TARGET);
		
		if(id != null) {
			builder.id(id.asText());
		}
		if(target != null) {
			builder.target(target.asText());
		}
						
		return builder.build();
	}

}
