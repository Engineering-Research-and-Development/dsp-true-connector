package it.eng.negotiation.transformer.to;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.Agreement;
import it.eng.tools.model.DSpaceConstants;

@Disabled("Until transformers are needed or for deletion")
public class JsonToAgreementTransformerTest extends AbstractToTransformerTest {

	private JsonToAgreementTransformer transformer = new JsonToAgreementTransformer();
	
	@Test
	public void trasnform() {
		Agreement agreement = transformer.transform(createJsonNode());

		assertNotNull(agreement, "Agreement cannot be null");
		assertNotNull(agreement.getId(), "Id cannot be null");
		assertNotNull(agreement.getAssignee(), "Assignee cannot be null");
		assertNotNull(agreement.getAssigner(), "Assigner cannot be null");
		assertNotNull(agreement.getTimestamp(), "Timestamp cannot be null");
	}

	@Override
	protected JsonNode createJsonNode() {
		Map<String, Object> in = new HashMap<>();
		in.put(DSpaceConstants.ID, generateUUID());
		in.put(DSpaceConstants.ODRL_ASSIGNEE, generateUUID());
		in.put(DSpaceConstants.ODRL_ASSIGNER, generateUUID());
		Map<String, Object> timestamp = new HashMap<>();
		timestamp.put(DSpaceConstants.VALUE, "2023-01-01T01:00:00Z");
		
		in.put(DSpaceConstants.DSPACE_TIMESTAMP, timestamp);
		
		return mapper.convertValue(in, JsonNode.class);
	}
}
