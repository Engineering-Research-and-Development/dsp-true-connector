package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class TransferTerminationMessageTest {

		private TransferTerminationMessage transferTerminationMessage = TransferTerminationMessage.Builder.newInstance()
				.consumerPid(ModelUtil.CONSUMER_PID)
				.providerPid(ModelUtil.PROVIDER_PID)
				.code("stop")
//				.reason(Arrays.asList("You got terminated!"))
				.build();
		
		@Test
		public void testPlain() {
			String result = Serializer.serializePlain(transferTerminationMessage);
			assertFalse(result.contains(DSpaceConstants.CONTEXT));
			assertFalse(result.contains(DSpaceConstants.TYPE));
			assertTrue(result.contains(DSpaceConstants.CONSUMER_PID));
			assertTrue(result.contains(DSpaceConstants.PROVIDER_PID));
			assertTrue(result.contains(DSpaceConstants.CODE));
			
			TransferTerminationMessage javaObj = Serializer.deserializePlain(result, TransferTerminationMessage.class);
			validateJavaObject(javaObj);
		}
		
		@Test
		public void testPlain_protocol() {
			String result = Serializer.serializeProtocol(transferTerminationMessage);
			assertTrue(result.contains(DSpaceConstants.CONTEXT));
			assertTrue(result.contains(DSpaceConstants.TYPE));
			assertTrue(result.contains(DSpaceConstants.DSPACE_CONSUMER_PID));
			assertTrue(result.contains(DSpaceConstants.DSPACE_PROVIDER_PID));
			assertTrue(result.contains(DSpaceConstants.DSPACE_CODE));
			
			TransferTerminationMessage javaObj = Serializer.deserializeProtocol(result, TransferTerminationMessage.class);
			validateJavaObject(javaObj);
		}
		
		@Test
		public void nonValid() {
			assertThrows(ValidationException.class,
					() -> TransferTerminationMessage.Builder.newInstance()
					.build());
		}

		public void validateJavaObject(TransferTerminationMessage javaObj) {
			assertNotNull(javaObj);
			assertNotNull(javaObj.getConsumerPid());
			assertNotNull(javaObj.getProviderPid());
			assertNotNull(javaObj.getCode());
		}
}
