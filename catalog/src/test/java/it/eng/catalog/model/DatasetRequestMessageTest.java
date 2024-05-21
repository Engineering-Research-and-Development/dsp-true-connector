package it.eng.catalog.model;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.serializer.Serializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DatasetRequestMessageTest {

	private DatasetRequestMessage datasetRequestMessage = DatasetRequestMessage.Builder.newInstance()
			.dataset("DATASET")
			.build();

	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = Serializer.serializePlain(datasetRequestMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DATASET));
		
		DatasetRequestMessage javaObj = Serializer.deserializePlain(result, DatasetRequestMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testPlain_protocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(datasetRequestMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_DATASET));
		
		DatasetRequestMessage javaObj = Serializer.deserializeProtocol(result, DatasetRequestMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(datasetRequestMessage);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, DatasetRequestMessage.class));
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class,
				() -> DatasetRequestMessage.Builder.newInstance()
					.build());
	}
	
	private void validateJavaObj(DatasetRequestMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getDataset());
	}
}
