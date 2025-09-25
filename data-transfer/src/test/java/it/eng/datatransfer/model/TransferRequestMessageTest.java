package it.eng.datatransfer.model;

import java.util.Arrays;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

import static org.junit.jupiter.api.Assertions.*;

public class TransferRequestMessageTest {
	
	private DataAddress dataAddress = DataAddress.Builder.newInstance()
			.endpoint(ModelUtil.ENDPOINT)
			.endpointType(ModelUtil.ENDPOINT_TYPE)
			.endpointProperties(Arrays.asList(
					EndpointProperty.Builder.newInstance().name("username").value("John").build(),
					EndpointProperty.Builder.newInstance().name("password").value("encodedPassword").build())
				)
			.build();
	
	private TransferRequestMessage transferRequestMessage = TransferRequestMessage.Builder.newInstance()
			.agreementId(ModelUtil.AGREEMENT_ID)
			.callbackAddress(ModelUtil.CALLBACK_ADDRESS)
			.consumerPid(ModelUtil.CONSUMER_PID)
			.format(ModelUtil.FORMAT)
			.dataAddress(dataAddress)
			.build();
	
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = TransferSerializer.serializePlain(transferRequestMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.AGREEMENT_ID));
		assertTrue(result.contains(DSpaceConstants.CALLBACK_ADDRESS));
		assertTrue(result.contains(DSpaceConstants.DATA_ADDRESS));
		assertTrue(result.contains(DSpaceConstants.ENDPOINT_PROPERTIES));
		
		TransferRequestMessage javaObj = TransferSerializer.deserializePlain(result, TransferRequestMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = TransferSerializer.serializeProtocolJsonNode(transferRequestMessage);
		JsonNode context = result.get(DSpaceConstants.CONTEXT);
		assertNotNull(context);
		if (context.isArray()) {
			ArrayNode arrayNode = (ArrayNode) context;
			assertFalse(arrayNode.isEmpty());
			assertEquals(DSpaceConstants.DSPACE_2025_01_CONTEXT, arrayNode.get(0).asText());
		}
		assertEquals(result.get(DSpaceConstants.TYPE).asText(), transferRequestMessage.getType());
		assertEquals(result.get(DSpaceConstants.CONSUMER_PID).asText(), transferRequestMessage.getConsumerPid());
		assertEquals(result.get(DSpaceConstants.AGREEMENT_ID).asText(), transferRequestMessage.getAgreementId());
		assertEquals(result.get(DSpaceConstants.CALLBACK_ADDRESS).asText(), transferRequestMessage.getCallbackAddress());
		assertEquals(result.get(DSpaceConstants.FORMAT).asText(), transferRequestMessage.getFormat());
		
		JsonNode dataAddress = result.get(DSpaceConstants.DATA_ADDRESS);
		assertNotNull(dataAddress);
		validateDataAddress(dataAddress);
		
		TransferRequestMessage javaObj = TransferSerializer.deserializeProtocol(result, TransferRequestMessage.class);
		validateJavaObject(javaObj);
	}

	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class,
				() -> TransferRequestMessage.Builder.newInstance()
				.build());
	}

	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = TransferSerializer.serializePlainJsonNode(transferRequestMessage);
		assertThrows(ValidationException.class, () -> TransferSerializer.deserializeProtocol(result, TransferRequestMessage.class));
	}
	
	private void validateJavaObject(TransferRequestMessage javaObj) {
		assertNotNull(javaObj);
		assertEquals(transferRequestMessage.getConsumerPid(), javaObj.getConsumerPid());
		assertEquals(transferRequestMessage.getAgreementId(), javaObj.getAgreementId());
		assertEquals(transferRequestMessage.getCallbackAddress(), javaObj.getCallbackAddress());
		assertEquals(transferRequestMessage.getFormat(), javaObj.getFormat());
		assertNotNull(javaObj.getDataAddress());

		DataAddress expected = transferRequestMessage.getDataAddress();
		assertEquals(expected.getEndpoint(), javaObj.getDataAddress().getEndpoint());
		assertEquals(expected.getEndpointType(), javaObj.getDataAddress().getEndpointType());
		assertNotNull(javaObj.getDataAddress().getEndpointProperties());
		assertEquals(expected.getEndpointProperties().size(), javaObj.getDataAddress().getEndpointProperties().size());

		for (int i = 0; i < expected.getEndpointProperties().size(); i++) {
			EndpointProperty exp = expected.getEndpointProperties().get(i);
			EndpointProperty actual = javaObj.getDataAddress().getEndpointProperties().get(i);
			assertEquals(exp.getName(), actual.getName());
			assertEquals(exp.getValue(), actual.getValue());
		}
	}
	
	private void validateDataAddress(JsonNode dataAddressForChecking) {
		assertEquals(dataAddressForChecking.get(DSpaceConstants.ENDPOINT_TYPE).asText(), dataAddress.getEndpointType());
		assertEquals(dataAddressForChecking.get(DSpaceConstants.ENDPOINT).asText(), dataAddress.getEndpoint());

		JsonNode properties = dataAddressForChecking.get(DSpaceConstants.ENDPOINT_PROPERTIES);
		assertNotNull(properties);
		if (properties.isArray()) {
			ArrayNode arrayNode = (ArrayNode) properties;
			assertFalse(arrayNode.isEmpty());
			for (int i = 0; i < arrayNode.size(); i++) {
				JsonNode prop = arrayNode.get(i);
				assertEquals(prop.get(DSpaceConstants.NAME).asText(), dataAddress.getEndpointProperties().get(i).getName());
				assertEquals(prop.get(DSpaceConstants.VALUE).asText(), dataAddress.getEndpointProperties().get(i).getValue());
			}
		}
	}
}
