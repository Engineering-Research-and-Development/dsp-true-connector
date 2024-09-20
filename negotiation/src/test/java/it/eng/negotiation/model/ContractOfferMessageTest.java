package it.eng.negotiation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.serializer.Serializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractOfferMessageTest {
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() throws JsonProcessingException {
		String result = Serializer.serializePlain(MockObjectUtil.CONTRACT_OFFER_MESSAGE);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
		assertTrue(result.contains(DSpaceConstants.OFFER));
		ContractOfferMessage javaObj = Serializer.deserializePlain(result, ContractOfferMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() throws JsonProcessingException {
		JsonNode result = Serializer.serializeProtocolJsonNode(MockObjectUtil.CONTRACT_OFFER_MESSAGE);
		assertNotNull(result.get(DSpaceConstants.CONTEXT));
		assertTrue(DSpaceConstants.validateContext(result.get(DSpaceConstants.CONTEXT)));
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CALLBACK_ADDRESS).asText());
		
		JsonNode offer = result.get(DSpaceConstants.DSPACE_OFFER);
		assertNotNull(offer);
		validateOfferProtocol(offer);

		ContractOfferMessage javaObj = Serializer.deserializeProtocol(result, ContractOfferMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractOfferMessage.Builder.newInstance()
					.build());
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(MockObjectUtil.CONTRACT_OFFER_MESSAGE);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, ContractOfferMessage.class));
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		ContractOfferMessage contractOfferMessage = MockObjectUtil.CONTRACT_OFFER_MESSAGE;
		String ss = Serializer.serializePlain(contractOfferMessage);
		ContractOfferMessage obj = Serializer.deserializePlain(ss, ContractOfferMessage.class);
		assertThat(contractOfferMessage)
			.usingRecursiveComparison().usingOverriddenEquals()
			.isEqualTo(obj);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		ContractOfferMessage contractOfferMessage = MockObjectUtil.CONTRACT_OFFER_MESSAGE;
		String ss = Serializer.serializeProtocol(contractOfferMessage);
		ContractOfferMessage obj = Serializer.deserializeProtocol(ss, ContractOfferMessage.class);
		assertThat(contractOfferMessage)
			.usingRecursiveComparison().usingOverriddenEquals()
			.isEqualTo(obj);
	}
	
	private void validateOfferProtocol(JsonNode offer) {
		assertNotNull(offer.get(DSpaceConstants.ODRL_ASSIGNEE).asText());
		assertNotNull(offer.get(DSpaceConstants.ODRL_ASSIGNER).asText());
		JsonNode permission = offer.get(DSpaceConstants.ODRL_PERMISSION).get(0);
		assertNotNull(permission.get(DSpaceConstants.ODRL_ACTION).asText());
		JsonNode constraint = permission.get(DSpaceConstants.ODRL_CONSTRAINT).get(0);
		assertNotNull(constraint.get(DSpaceConstants.ODRL_LEFT_OPERAND).asText());
		assertNotNull(constraint.get(DSpaceConstants.ODRL_OPERATOR).asText());
		assertNotNull(constraint.get(DSpaceConstants.ODRL_RIGHT_OPERAND).asText());
	}
	
	private void validateJavaObj(ContractOfferMessage javaObj) {
		assertNotNull(javaObj);
		assertEquals(MockObjectUtil.CONSUMER_PID, javaObj.getConsumerPid());
		assertEquals(MockObjectUtil.PROVIDER_PID, javaObj.getProviderPid());
		assertEquals(MockObjectUtil.CALLBACK_ADDRESS, javaObj.getCallbackAddress());
		assertNotNull(javaObj.getOffer());
	}
	
}
