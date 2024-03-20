package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractNegotiationTerminationMessageTest {

	private ContractNegotiationTerminationMessage contractNegotiationTerminationMessage = ContractNegotiationTerminationMessage.Builder
			.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.code("Termination CD_123")
			.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value("Meaningful reas for term").build(),
					Reason.Builder.newInstance().language("it").value("Meaningful reas for term but in Italian").build()))
			.build();
	
	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(contractNegotiationTerminationMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CODE));
		assertTrue(result.contains(DSpaceConstants.REASON));
		
		ContractNegotiationTerminationMessage javaObj = Serializer.deserializePlain(result, ContractNegotiationTerminationMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	public void testProtocol() {
		String result = Serializer.serializeProtocol(contractNegotiationTerminationMessage);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CODE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_REASON));
		
		ContractNegotiationTerminationMessage javaObj = Serializer.deserializeProtocol(result, ContractNegotiationTerminationMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractNegotiationTerminationMessage.Builder.newInstance()
					.build());
	}
	
	private void validateJavaObj(ContractNegotiationTerminationMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getCode());
		// must be 2 descriptions
		assertEquals(2, javaObj.getReason().size());
	}
}
