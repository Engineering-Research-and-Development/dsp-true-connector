package it.eng.datatransfer.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

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
	@DisplayName("Verify valid plain object serialization")
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
	@DisplayName("Verify valid protocol object serialization")
	public void testPlain_protocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(transferError);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText());
		
		TransferError javaObj = Serializer.deserializeProtocol(result, TransferError.class);
		validateJavaObject(javaObj);
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class,
				() -> TransferError.Builder.newInstance()
				.build());
	}
	
	@Test
	@DisplayName("Missing @context and @ype")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(transferError);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, TransferError.class));
	}

	public void validateJavaObject(TransferError javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConsumerPid());
		assertNotNull(javaObj.getProviderPid());
		assertNotNull(javaObj.getCode());
	}
}
