package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class TransferRequestMessageTest {
	
	private DataAddress dataAddress = DataAddress.Builder.newInstance()
			.endpoint(ModelUtil.ENDPOINT)
			.endpointType(ModelUtil.ENDPOINT_TYPE)
			.endpointProperties(Arrays.asList(
					EndpointProperty.Builder.newInstance().name("username").vaule("John").build(),
					EndpointProperty.Builder.newInstance().name("password").vaule("encodedPassword").build())
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
	public void testPlain() {
		String result = Serializer.serializePlain(transferRequestMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.AGREEMENT_ID));
		
		TransferRequestMessage javaObj = Serializer.deserializePlain(result, TransferRequestMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	public void testProtocol() {
		String result = Serializer.serializeProtocol(transferRequestMessage);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_AGREEMENT_ID));
		
		TransferRequestMessage javaObj = Serializer.deserializeProtocol(result, TransferRequestMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	public void nonValid() {
		assertThrows(ValidationException.class,
				() -> TransferRequestMessage.Builder.newInstance()
				.build());
	}

	public void validateJavaObject(TransferRequestMessage javaObj) {
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
}
