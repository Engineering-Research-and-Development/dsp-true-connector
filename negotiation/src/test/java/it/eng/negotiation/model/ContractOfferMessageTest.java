package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractOfferMessageTest {
	
	@Test
	public void testPlain() throws JsonProcessingException {
		String result = Serializer.serializePlain(ModelUtil.CONTRACT_OFFER_MESSAGE);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertFalse(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
		assertTrue(result.contains(DSpaceConstants.OFFER));
		ContractOfferMessage javaObj = Serializer.deserializePlain(result, ContractOfferMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	public void testProtocol() throws JsonProcessingException {
		String result = Serializer.serializeProtocol(ModelUtil.CONTRACT_OFFER_MESSAGE);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CALLBACK_ADDRESS));
		assertTrue(result.contains(DSpaceConstants.DSPACE_OFFER));

		ContractOfferMessage javaObj = Serializer.deserializeProtocol(result, ContractOfferMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractOfferMessage.Builder.newInstance()
					.build());
	}
	
	private void validateJavaObj(ContractOfferMessage javaObj) {
		assertNotNull(javaObj);
		assertEquals(ModelUtil.CONSUMER_PID, javaObj.getConsumerPid());
		assertEquals(ModelUtil.PROVIDER_PID, javaObj.getProviderPid());
		assertEquals(ModelUtil.CALLBACK_ADDRESS, javaObj.getCallbackAddress());
		assertNotNull(javaObj.getOffer());
	}
	
}
