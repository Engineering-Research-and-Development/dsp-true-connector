package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractRequestMessageTest {

	private ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
			.offer(Offer.Builder.newInstance().build())
			.build();
	
	@Test
	public void testPlain() throws JsonProcessingException {
		String result = Serializer.serializePlain(contractRequestMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertFalse(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
		
		ContractRequestMessage javaObj = Serializer.deserializePlain(result, ContractRequestMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	public void testContractRequest_consumer() {
		ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
				.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
				.offer(Offer.Builder.newInstance().build())
				.build();
		String result = Serializer.serializePlain(contractRequestMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertFalse(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertFalse(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
	}
	
	@Test
	public void testPlain_offer() throws JsonProcessingException {
		Offer offer = Offer.Builder.newInstance()
				.target(ModelUtil.TARGET)
				.build();
		ContractRequestMessage contractRequestMessageOffer = ContractRequestMessage.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
				.providerPid(ModelUtil.PROVIDER_PID)
				.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
				.offer(offer)
				.build();
		String result = Serializer.serializePlain(contractRequestMessageOffer);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertFalse(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
		
		ContractRequestMessage javaObj = Serializer.deserializePlain(result, ContractRequestMessage.class);
		validateJavaObj(javaObj);
		assertNotNull(javaObj.getOffer().getId());
		assertNotNull(javaObj.getOffer().getTarget());
	}
	
	@Test
	public void testProtocol() throws JsonProcessingException {
		String result = Serializer.serializeProtocol(contractRequestMessage);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CALLBACK_ADDRESS));
		
		ContractRequestMessage javaObj = Serializer.deserializeProtocol(result, ContractRequestMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	public void testProtocol_offer() throws JsonProcessingException {
		Offer offer = Offer.Builder.newInstance()
				.target(UUID.randomUUID().toString())
				.build();
		ContractRequestMessage contractRequestMessageOffer = ContractRequestMessage.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
				.providerPid(ModelUtil.PROVIDER_PID)
				.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
				.offer(offer)
				.build();
		String result = Serializer.serializeProtocol(contractRequestMessageOffer);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CALLBACK_ADDRESS));
		// offer
		assertTrue(result.contains(DSpaceConstants.DSPACE_OFFER));
		assertTrue(result.contains(DSpaceConstants.ODRL_TARGET));
		
		ContractRequestMessage javaObj = Serializer.deserializeProtocol(result, ContractRequestMessage.class);
		validateJavaObj(javaObj);
		assertNotNull(javaObj.getOffer().getId());
		assertNotNull(javaObj.getOffer().getTarget());
	}
	
	@Test
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractRequestMessage.Builder.newInstance()
					.build());
	}
	
	private void validateJavaObj(ContractRequestMessage javaObj) {
		assertNotNull(javaObj);
		assertEquals(ModelUtil.CONSUMER_PID, javaObj.getConsumerPid());
		assertEquals(ModelUtil.PROVIDER_PID, javaObj.getProviderPid());
		assertEquals(ModelUtil.CALLBACK_ADDRESS, javaObj.getCallbackAddress());
		assertNotNull(javaObj.getOffer());
	}
}
