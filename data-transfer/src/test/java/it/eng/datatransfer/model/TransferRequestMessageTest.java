package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

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
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_AGREEMENT_ID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CALLBACK_ADDRESS).asText());
		
		JsonNode dataAddres = result.get(DSpaceConstants.DSPACE_DATA_ADDRESS);
		assertNotNull(dataAddres);
		validateDataAddress(dataAddres);
		
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
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getAgreementId());
		assertNotNull(javaObj.getCallbackAddress());
		assertNotNull(javaObj.getFormat());
		assertNotNull(javaObj.getDataAddress());
		
		assertNotNull(javaObj.getDataAddress().getEndpoint());
		assertNotNull(javaObj.getDataAddress().getEndpointType());
		assertNotNull(javaObj.getDataAddress().getEndpointProperties());

		assertNotNull(javaObj.getDataAddress().getEndpointProperties().get(0).getName());
		assertNotNull(javaObj.getDataAddress().getEndpointProperties().get(0).getValue());
		assertNotNull(javaObj.getDataAddress().getEndpointProperties().get(1).getName());
		assertNotNull(javaObj.getDataAddress().getEndpointProperties().get(1).getValue());
	}
	
	private void validateDataAddress(JsonNode dataAddress) {
		assertNotNull(dataAddress.get(DSpaceConstants.DSPACE_ENDPOINT_TYPE).asText());
		assertNotNull(dataAddress.get(DSpaceConstants.DSPACE_ENDPOINT).asText());
		assertNotNull(dataAddress.get(DSpaceConstants.DSPACE_ENDPOINT_PROPERTIES));
	}
}
