package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class TransferSuspensionMessageTest {

	private TransferSuspensionMessage transferSuspensionMessage = TransferSuspensionMessage.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.code("pause")
//			.reason(Arrays.asList("I got tierd"))
			.build();
	
	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(transferSuspensionMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.CODE));
		
		TransferSuspensionMessage javaObj = Serializer.deserializePlain(result, TransferSuspensionMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	public void testPlain_protocol() {
		String result = Serializer.serializeProtocol(transferSuspensionMessage);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CODE));
		
		TransferSuspensionMessage javaObj = Serializer.deserializeProtocol(result, TransferSuspensionMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	public void nonValid() {
		assertThrows(ValidationException.class,
				() -> TransferSuspensionMessage.Builder.newInstance()
				.build());
	}

	public void validateJavaObject(TransferSuspensionMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getCode());
	}
}
