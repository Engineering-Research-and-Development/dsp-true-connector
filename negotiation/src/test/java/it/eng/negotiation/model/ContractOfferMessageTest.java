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

import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class ContractOfferMessageTest {
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() throws JsonProcessingException {
		String result = NegotiationSerializer.serializePlain(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
		assertTrue(result.contains(DSpaceConstants.OFFER));
		ContractOfferMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractOfferMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() throws JsonProcessingException {
		JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CALLBACK_ADDRESS).asText());
		
		JsonNode offer = result.get(DSpaceConstants.DSPACE_OFFER);
		assertNotNull(offer);
		validateOfferProtocol(offer);

		ContractOfferMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractOfferMessage.class);
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
		JsonNode result = NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE);
		assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractOfferMessage.class));
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		ContractOfferMessage contractOfferMessage = NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE;
		String ss = NegotiationSerializer.serializePlain(contractOfferMessage);
		ContractOfferMessage obj = NegotiationSerializer.deserializePlain(ss, ContractOfferMessage.class);
		assertThat(contractOfferMessage).usingRecursiveComparison().isEqualTo(obj);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		ContractOfferMessage contractOfferMessage = NegotiationMockObjectUtil.CONTRACT_OFFER_MESSAGE;
		String ss = NegotiationSerializer.serializeProtocol(contractOfferMessage);
		ContractOfferMessage obj = NegotiationSerializer.deserializeProtocol(ss, ContractOfferMessage.class);
		assertThat(contractOfferMessage).usingRecursiveComparison().isEqualTo(obj);
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
		assertEquals(NegotiationMockObjectUtil.CONSUMER_PID, javaObj.getConsumerPid());
		assertEquals(NegotiationMockObjectUtil.PROVIDER_PID, javaObj.getProviderPid());
		assertEquals(NegotiationMockObjectUtil.CALLBACK_ADDRESS, javaObj.getCallbackAddress());
		assertNotNull(javaObj.getOffer());
	}
	
}
