package it.eng.negotiation.transformer.to;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractNegotiationTerminationMessage;
import it.eng.tools.model.DSpaceConstants;

public class JsonToContractNegotiationTerminationMessageTransformerTest extends AbstractToTransformerTest {

	private JsonToContractNegotiationTerminationMessageTransformer transformer = new JsonToContractNegotiationTerminationMessageTransformer();
	
	@Test
	public void transform() {
		ContractNegotiationTerminationMessage contractNegotiationTerminationMessage = transformer.transform(createJsonNode());
		assertNotNull(contractNegotiationTerminationMessage, "Contract Negotiation Termination Message must not be null");
		assertNotNull(contractNegotiationTerminationMessage.getConsumerPid(), "Consumer should not be null");
		assertNotNull(contractNegotiationTerminationMessage.getProviderPid(), "Provider should not be null");
		assertNotNull(contractNegotiationTerminationMessage.getReason(), "Reason should not be null");
		assertEquals(contractNegotiationTerminationMessage.getReason().size(), 2);
	}
	
	@Test
	public void transformSingleReason() {
		ContractNegotiationTerminationMessage contractNegotiationTerminationMessage = transformer.transform(createJsonNode1());
		assertNotNull(contractNegotiationTerminationMessage, "Contract Negotiation Termination Message must not be null");
		assertNotNull(contractNegotiationTerminationMessage.getConsumerPid(), "Consumer should not be null");
		assertNotNull(contractNegotiationTerminationMessage.getProviderPid(), "Provider should not be null");
		assertNotNull(contractNegotiationTerminationMessage.getReason(), "Reason should not be null");
		assertEquals(contractNegotiationTerminationMessage.getReason().size(), 1);
	}

	private JsonNode createJsonNode1() {
		Map<String, Object> in = createJsonNodeInternal(1);
		return mapper.convertValue(in, JsonNode.class);
	}
	
	@Override
	protected JsonNode createJsonNode() {
		Map<String, Object> in = createJsonNodeInternal(2);
		return mapper.convertValue(in, JsonNode.class);
	}

	public Map<String, Object> createJsonNodeInternal(int reasonsNo) {
		Map<String, Object> in = new HashMap<>();
		in.put(DSpaceConstants.CONTEXT, DSpaceConstants.CATALOG_CONTEXT_VALUE);
		in.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + transformer.getOutputType().getSimpleName());
		in.put(DSpaceConstants.DSPACE_CONSUMER_PID, "urn:uuid:" + UUID.randomUUID());
		in.put(DSpaceConstants.DSPACE_PROVIDER_PID, "urn:uuid:" + UUID.randomUUID());
		List<Map<String, Object>> reasons = new ArrayList<>();
		for(int i = 0; i < reasonsNo; i++) {
			Map<String, Object> reason = new HashMap<>();
			reason.put(DSpaceConstants.VALUE, "Reason for rejection " + i+1);
			reason.put(DSpaceConstants.LANGUAGE, "en");
			reasons.add(reason);
		}
		in.put(DSpaceConstants.DSPACE_REASON, reasons);
		return in;
	}
}
