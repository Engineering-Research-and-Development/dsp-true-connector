package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractNegotiationTest {

	private ContractNegotiation contractNegotiation = ContractNegotiation.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.state(ContractNegotiationState.ACCEPTED)
			.build();
	
	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(contractNegotiation);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains("ACCEPTED"));
		
		ContractNegotiation javaObj = Serializer.deserializePlain(result, ContractNegotiation.class);
		validateJavaObj(javaObj);
	}

	@Test
	public void testProtocol() {
		String result = Serializer.serializeProtocol(contractNegotiation);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_STATE));
		
		ContractNegotiation javaObj = Serializer.deserializeProtocol(result, ContractNegotiation.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractNegotiation.Builder.newInstance()
					.build());
	}
	
	private void validateJavaObj(ContractNegotiation javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getState());
	}
}
