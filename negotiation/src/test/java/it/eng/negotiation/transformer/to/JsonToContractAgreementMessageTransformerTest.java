package it.eng.negotiation.transformer.to;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractAgreementMessage;
import it.eng.tools.model.DSpaceConstants;

@Disabled("Until transformers are needed or for deletion")
public class JsonToContractAgreementMessageTransformerTest extends AbstractToTransformerTest {

	private JsonToContractAgreementMessageTransformer transformer = new JsonToContractAgreementMessageTransformer();
	
	@Test
	public void trasnform() {
		ContractAgreementMessage contractAgreementMessage = transformer.transform(createJsonNode());
		
		assertNotNull(contractAgreementMessage, "Agreement cannot be null");
		assertNotNull(contractAgreementMessage.getCallbackAddress(), "Callback address should not be null");
		assertNotNull(contractAgreementMessage.getAgreement(), "Agreement cannot be null");
		assertNotNull(contractAgreementMessage.getAgreement().getId(), "Agreement Id cannot be null");
	}

	@Override
	protected JsonNode createJsonNode() {
		Map<String, Object> in = new HashMap<>();
		in.put(DSpaceConstants.CONTEXT, DSpaceConstants.CATALOG_CONTEXT_VALUE);
		in.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + transformer.getOutputType().getSimpleName());
		in.put(DSpaceConstants.DSPACE_CALLBACK_ADDRESS, "https://callback.address.test.mock");
		in.put(DSpaceConstants.DSPACE_CONSUMER_PID, "urn:uuid:" + UUID.randomUUID());
		in.put(DSpaceConstants.DSPACE_PROVIDER_PID, "urn:uuid:" + UUID.randomUUID());

		Map<String, Object> agreement = new HashMap<>();
		agreement.put(DSpaceConstants.ID, generateUUID());
		agreement.put(DSpaceConstants.DSPACE_CONSUMER_PID, generateUUID());
		agreement.put(DSpaceConstants.DSPACE_PROVIDER_PID, generateUUID());
		Map<String, Object> timestamp = new HashMap<>();
		timestamp.put(DSpaceConstants.VALUE, "2023-01-01T01:00:00Z");
		agreement.put(DSpaceConstants.DSPACE_TIMESTAMP, timestamp);
		
		in.put(DSpaceConstants.DSPACE_AGREEMENT, agreement);
		return mapper.convertValue(in, JsonNode.class);
	}
}
