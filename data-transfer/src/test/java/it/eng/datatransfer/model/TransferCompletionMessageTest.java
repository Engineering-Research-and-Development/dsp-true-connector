package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class TransferCompletionMessageTest {

	private TransferCompletionMessage transferCompletionMessage = TransferCompletionMessage.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.build();
		
	
	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(transferCompletionMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		
		TransferCompletionMessage javaObj = Serializer.deserializePlain(result, TransferCompletionMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	public void testPlain_protocol() {
		String result = Serializer.serializeProtocol(transferCompletionMessage);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		
		TransferCompletionMessage javaObj = Serializer.deserializeProtocol(result, TransferCompletionMessage.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	public void nonValid() {
		assertThrows(ValidationException.class,
				() -> TransferCompletionMessage.Builder.newInstance()
				.build());
	}

	public void validateJavaObject(TransferCompletionMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
	}
}
