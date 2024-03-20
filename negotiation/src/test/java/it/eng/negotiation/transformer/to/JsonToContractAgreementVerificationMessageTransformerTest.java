package it.eng.negotiation.transformer.to;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractAgreementVerificationMessage;
import it.eng.tools.model.DSpaceConstants;

public class JsonToContractAgreementVerificationMessageTransformerTest extends AbstractToTransformerTest{

	private JsonToContractAgreementVerificationMessageTransformer transformer = new JsonToContractAgreementVerificationMessageTransformer();
	
	@Test
	public void transform() {
		ContractAgreementVerificationMessage contractAgreementVerificationMessage = transformer.transform(createJsonNode());
		assertNotNull(contractAgreementVerificationMessage, "Contract Agreement Verification Message must not be null");
		assertNotNull(contractAgreementVerificationMessage.getConsumerPid(), "Consumer should not be null");
		assertNotNull(contractAgreementVerificationMessage.getProviderPid(), "Provider should not be null");
	}

	@Override
	protected JsonNode createJsonNode() {
		Map<String, Object> in = new HashMap<>();
		in.put(DSpaceConstants.CONTEXT, DSpaceConstants.CATALOG_CONTEXT_VALUE);
		in.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + transformer.getOutputType().getSimpleName());
		in.put(DSpaceConstants.DSPACE_CONSUMER_PID, "urn:uuid:" + UUID.randomUUID());
		in.put(DSpaceConstants.DSPACE_PROVIDER_PID, "urn:uuid:" + UUID.randomUUID());
		return mapper.convertValue(in, JsonNode.class);
	}
}
