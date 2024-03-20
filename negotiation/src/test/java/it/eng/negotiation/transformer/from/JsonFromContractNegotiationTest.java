package it.eng.negotiation.transformer.from;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.ModelUtil;
import it.eng.tools.model.DSpaceConstants;

public class JsonFromContractNegotiationTest {

	private JsonFromContractNegotiationTransformer transformer = new JsonFromContractNegotiationTransformer();
	
	@Test
	public void transformToJson() {
		ContractNegotiation message = 
				ContractNegotiation.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
				.providerPid(ModelUtil.PROVIDER_PID)
				.state(ContractNegotiationState.ACCEPTED)
				.build();
		
		var jsonNode = transformer.transform(message);
		
		assertNotNull(jsonNode);
		assertEquals(DSpaceConstants.CATALOG_CONTEXT_VALUE, jsonNode.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(jsonNode.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(jsonNode.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertEquals(DSpaceConstants.DSPACE + ContractNegotiation.class.getSimpleName(), jsonNode.get(DSpaceConstants.TYPE).asText());
		assertEquals(ContractNegotiationState.ACCEPTED.toString(), 
				jsonNode.get(DSpaceConstants.DSPACE + DSpaceConstants.STATE).asText());
	}
	
}
