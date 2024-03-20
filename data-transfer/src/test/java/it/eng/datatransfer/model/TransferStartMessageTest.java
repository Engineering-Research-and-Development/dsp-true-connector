package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class TransferStartMessageTest {

	private DataAddress dataAddress = DataAddress.Builder.newInstance()
			.endpoint(ModelUtil.ENDPOINT)
			.endpointType(ModelUtil.ENDPOINT_TYPE)
			.endpointProperties(Arrays.asList(
					EndpointProperty.Builder.newInstance().name("username").vaule("John").build(),
					EndpointProperty.Builder.newInstance().name("password").vaule("encodedPassword").build())
				)
			.build();
	
	private TransferStartMessage transferStartMessage = TransferStartMessage.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.dataAddress(dataAddress)
			.build();
	
	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(transferStartMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.DATA_ADDRESS));
		
		TransferStartMessage javaObj = Serializer.deserializePlain(result, TransferStartMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	public void testProtocol() {
		String result = Serializer.serializeProtocol(transferStartMessage);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_DATA_ADDRESS));
		
		TransferStartMessage javaObj = Serializer.deserializeProtocol(result, TransferStartMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	public void nonValid() {
		assertThrows(ValidationException.class,
				() -> TransferStartMessage.Builder.newInstance()
				.build());
	}

	public void validateJavaObject(TransferStartMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
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
