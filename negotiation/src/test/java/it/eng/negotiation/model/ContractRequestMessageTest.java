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

public class ContractRequestMessageTest {

	private ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
			.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
			.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
			.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
			.offer(NegotiationMockObjectUtil.OFFER)
			.build();
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() throws JsonProcessingException {
		String result = NegotiationSerializer.serializePlain(contractRequestMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
		
		ContractRequestMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractRequestMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Verify valid plain object serialization - initial ContractRequestMessage")
	public void testContractRequest_consumer() {
		ContractRequestMessage contractRequestMessage = ContractRequestMessage.Builder.newInstance()
				.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
				.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
				.offer(NegotiationMockObjectUtil.OFFER)
				.build();
		String result = NegotiationSerializer.serializePlain(contractRequestMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
	}
	
	@Test
	@DisplayName("Verify valid plain object serialization - contains offer")
	public void testPlain_offer() throws JsonProcessingException {
		ContractRequestMessage contractRequestMessageOffer = ContractRequestMessage.Builder.newInstance()
				.consumerPid(NegotiationMockObjectUtil.CONSUMER_PID)
				.providerPid(NegotiationMockObjectUtil.PROVIDER_PID)
				.callbackAddress(NegotiationMockObjectUtil.CALLBACK_ADDRESS)
				.offer(NegotiationMockObjectUtil.OFFER)
				.build();
		String result = NegotiationSerializer.serializePlain(contractRequestMessageOffer);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
		
		ContractRequestMessage javaObj = NegotiationSerializer.deserializePlain(result, ContractRequestMessage.class);
		validateJavaObj(javaObj);
		assertNotNull(javaObj.getOffer().getId());
		assertNotNull(javaObj.getOffer().getTarget());
	}
	
	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() throws JsonProcessingException {
		JsonNode result = NegotiationSerializer.serializeProtocolJsonNode(contractRequestMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CALLBACK_ADDRESS).asText());
		
		validateOfferProtocol(result.get(DSpaceConstants.DSPACE_OFFER));
		
		ContractRequestMessage javaObj = NegotiationSerializer.deserializeProtocol(result, ContractRequestMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class, 
				() -> ContractRequestMessage.Builder.newInstance()
					.build());
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = NegotiationSerializer.serializePlainJsonNode(contractRequestMessage);
		assertThrows(ValidationException.class, () -> NegotiationSerializer.deserializeProtocol(result, ContractRequestMessage.class));
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = NegotiationSerializer.serializePlain(contractRequestMessage);
		ContractRequestMessage obj = NegotiationSerializer.deserializePlain(ss, ContractRequestMessage.class);
		assertThat(contractRequestMessage).usingRecursiveComparison().isEqualTo(obj);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		String ss = NegotiationSerializer.serializeProtocol(contractRequestMessage);
		ContractRequestMessage obj = NegotiationSerializer.deserializeProtocol(ss, ContractRequestMessage.class);
		assertThat(contractRequestMessage).usingRecursiveComparison().isEqualTo(obj);
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
	
	private void validateJavaObj(ContractRequestMessage javaObj) {
		assertNotNull(javaObj);
		assertEquals(NegotiationMockObjectUtil.CONSUMER_PID, javaObj.getConsumerPid());
		assertEquals(NegotiationMockObjectUtil.PROVIDER_PID, javaObj.getProviderPid());
		assertEquals(NegotiationMockObjectUtil.CALLBACK_ADDRESS, javaObj.getCallbackAddress());
		assertNotNull(javaObj.getOffer());
	}
}
