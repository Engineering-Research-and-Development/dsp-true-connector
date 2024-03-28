package it.eng.negotiation.transformer.from;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import it.eng.negotiation.model.ContractRequestMessage;
import it.eng.negotiation.model.ModelUtil;
import it.eng.tools.model.DSpaceConstants;

public class JsonFromContractRequestMessageTransformerTest {

	private JsonFromContractRequestMessageTransformer transformer = new JsonFromContractRequestMessageTransformer();
	
	@Test
	public void transform() {
		ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
				.callbackAddress("https://callback.address.test.mock")
				.offer(ModelUtil.OFFER)
				.consumerPid(ModelUtil.CONSUMER_PID)
				.build();
		
		var jsonNode = transformer.transform(contractRequestMessage);
		
		assertNotNull(jsonNode, "Contract request message cannot be null");
		assertEquals(DSpaceConstants.CATALOG_CONTEXT_VALUE, jsonNode.get(DSpaceConstants.CONTEXT).asText());
		assertEquals(DSpaceConstants.DSPACE + ContractRequestMessage.class.getSimpleName(), jsonNode.get(DSpaceConstants.TYPE).asText());
		assertNotNull(jsonNode.get(DSpaceConstants.DSPACE_CALLBACK_ADDRESS).asText());
		assertNotNull(jsonNode.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
	}
}
