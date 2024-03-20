package it.eng.negotiation.transformer.to;

import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.Permission;
import it.eng.negotiation.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonToAgreementTransformer implements TransformInterface<JsonNode, Agreement> {

	@Override
	public Class<JsonNode> getInputType() {
		return JsonNode.class;
	}

	@Override
	public Class<Agreement> getOutputType() {
		return Agreement.class;
	}

	@Override
	public Agreement transform(JsonNode input) {
		var builder = Agreement.Builder.newInstance();
				
		JsonNode id = input.get(DSpaceConstants.ID);
		JsonNode assignee = input.get(DSpaceConstants.ODRL_ASSIGNEE);
		JsonNode assigner = input.get(DSpaceConstants.ODRL_ASSIGNER);
		
		if(id != null ) {
			builder.id(id.asText());
		}
		if(assignee != null ) {
			builder.assignee(assignee.asText());
		}
		if(assigner != null ) {
			builder.assigner(assigner.asText());
		}
		
		//JsonNode permissionJson = input.get(DSpaceConstants.ODRL + DSpaceConstants.PERMISSION);
		
		var permissionBuilder = Permission.Builder.newInstance();
		builder.permission(Arrays.asList(permissionBuilder.build()));
		
		JsonNode timestampJson = input.get(DSpaceConstants.DSPACE_TIMESTAMP);
		
		JsonNode timestampJsonValue = timestampJson.get(DSpaceConstants.VALUE);
		if(timestampJsonValue != null) {
			builder.timestamp(timestampJsonValue.asText());
		}
		
		return builder.build();
	}

}
