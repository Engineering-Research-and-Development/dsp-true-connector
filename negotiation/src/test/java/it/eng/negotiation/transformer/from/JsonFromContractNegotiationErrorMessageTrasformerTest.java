package it.eng.negotiation.transformer.from;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.model.Description;
import it.eng.negotiation.model.Reason;
import it.eng.tools.model.DSpaceConstants;

public class JsonFromContractNegotiationErrorMessageTrasformerTest {

	private JsonFromContractNegotiationErrorMessageTrasformer transformer = new JsonFromContractNegotiationErrorMessageTrasformer();
	
	@Test
	public void trasnform() {
		ContractNegotiationErrorMessage contractNegotiationErrorMessage = ContractNegotiationErrorMessage.Builder.newInstance()
				.code("CODE123")
				.description(Arrays.asList(Description.Builder.newInstance().language("EN").value("Description used in tests").build()))
				.reason(Arrays.asList(Reason.Builder.newInstance().language("EN").value("Reason Test").build()))
				.consumerPid("urn:uuid:" + UUID.randomUUID())
				.providerPid("urn:uuid:" + UUID.randomUUID())
				.build();
		var jsonNode = transformer.transform(contractNegotiationErrorMessage);
		
		assertNotNull(jsonNode);
		assertEquals(DSpaceConstants.CATALOG_CONTEXT_VALUE, jsonNode.get(DSpaceConstants.CONTEXT).asText());
		assertEquals(DSpaceConstants.DSPACE + ContractNegotiationErrorMessage.class.getSimpleName(), jsonNode.get(DSpaceConstants.TYPE).asText());
		assertNotNull(jsonNode.get(DSpaceConstants.DSPACE_CODE).asText());
		assertNotNull(jsonNode.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(jsonNode.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(jsonNode.get(DSpaceConstants.DSPACE_REASON).get(0));
		assertNotNull(jsonNode.get(DSpaceConstants.DCT_DESCRIPTION).get(0));
	}
}
