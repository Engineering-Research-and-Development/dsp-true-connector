package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class TransferErrorTest {
	TransferError transferError = TransferError.Builder.newInstance()
			.consumerPid(ModelUtil.CONSUMER_PID)
			.providerPid(ModelUtil.PROVIDER_PID)
			.code("Hit fan")
//			.reason(Arrays.asList("We hit something"))
			.build();
	
	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(transferError);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
		
		TransferError javaObj = Serializer.deserializePlain(result, TransferError.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	public void testPlain_protocol() {
		String result = Serializer.serializeProtocol(transferError);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
		assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
		
		TransferError javaObj = Serializer.deserializeProtocol(result, TransferError.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	public void nonValid() {
		assertThrows(ValidationException.class,
				() -> TransferError.Builder.newInstance()
				.build());
	}

	public void validateJavaObject(TransferError javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getCode());
	}
}
