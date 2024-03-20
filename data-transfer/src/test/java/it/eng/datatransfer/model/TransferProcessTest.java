package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class TransferProcessTest {

	private TransferProcess transferProcess = TransferProcess.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.state(TransferState.STARTED)
			.build();
	
	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(transferProcess);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		
		TransferProcess javaObj = Serializer.deserializePlain(result, TransferProcess.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	public void testPlain_protocol() {
		String result = Serializer.serializeProtocol(transferProcess);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		
		TransferProcess javaObj = Serializer.deserializeProtocol(result, TransferProcess.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	public void nonValid() {
		assertThrows(ValidationException.class,
				() -> TransferProcess.Builder.newInstance()
				.build());
	}

	public void validateJavaObject(TransferProcess javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getState());
	}
}
