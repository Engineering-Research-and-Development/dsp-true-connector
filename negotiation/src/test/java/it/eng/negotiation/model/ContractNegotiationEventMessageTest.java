package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractNegotiationEventMessageTest {

	private ContractNegotiationEventMessage contractNegotiationEventMessage = ContractNegotiationEventMessage.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.eventType(ContractNegotiationEventType.ACCEPTED)
			.build();
	
	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(contractNegotiationEventMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.EVENT_TYPE));
		
		ContractNegotiationEventMessage javaObj = Serializer.deserializePlain(result, ContractNegotiationEventMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	public void testProtocol() {
		String result = Serializer.serializeProtocol(contractNegotiationEventMessage);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_EVENT_TYPE));
		
		ContractNegotiationEventMessage javaObj = Serializer.deserializeProtocol(result, ContractNegotiationEventMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractNegotiationEventMessage.Builder.newInstance()
					.build());
	}
	
	private void validateJavaObj(ContractNegotiationEventMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getEventType());
	}
	
}
