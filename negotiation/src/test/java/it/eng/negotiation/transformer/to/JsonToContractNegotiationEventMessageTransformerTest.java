package it.eng.negotiation.transformer.to;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractNegotiationEventMessage;
import it.eng.negotiation.model.ContractNegotiationEventType;
import it.eng.tools.model.DSpaceConstants;

public class JsonToContractNegotiationEventMessageTransformerTest extends AbstractToTransformerTest {

	private JsonToContractNegotiationEventMessageTransformer transformer = new JsonToContractNegotiationEventMessageTransformer();
	
	@Test
	public void transform() {
		ContractNegotiationEventMessage message = transformer.transform(createJsonNode());
		assertNotNull(message, "ContractNegotiationEventMessage cannot be null");
		assertNotNull(message.getConsumerPid(), "Consumer should not be null");
		assertNotNull(message.getProviderPid(), "Provider should not be null");
		assertNotNull(message.getEventType(), "Event type should not be null");
	}

	@Override
	protected JsonNode createJsonNode() {
		Map<String, Object> in = new HashMap<>();
		in.put(DSpaceConstants.CONTEXT, DSpaceConstants.CATALOG_CONTEXT_VALUE);
		in.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + transformer.getOutputType().getSimpleName());
		in.put(DSpaceConstants.DSPACE_CONSUMER_PID, "urn:uuid:" + UUID.randomUUID());
		in.put(DSpaceConstants.DSPACE_PROVIDER_PID, "urn:uuid:" + UUID.randomUUID());
		in.put(DSpaceConstants.DSPACE_EVENT_TYPE, ContractNegotiationEventType.FINALIZED.toString());
		return mapper.convertValue(in, JsonNode.class);
	}
}
