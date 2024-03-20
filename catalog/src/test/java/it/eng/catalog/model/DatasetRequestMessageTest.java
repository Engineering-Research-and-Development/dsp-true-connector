package it.eng.catalog.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class DatasetRequestMessageTest {

	private DatasetRequestMessage datasetRequestMessage = DatasetRequestMessage.Builder.newInstance()
			.dataset("DATASET")
			.build();

	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(datasetRequestMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DATASET));
		
		DatasetRequestMessage javaObj = Serializer.deserializePlain(result, DatasetRequestMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	public void testPlain_protocol() {
		String result = Serializer.serializeProtocol(datasetRequestMessage);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_DATASET));
		
		DatasetRequestMessage javaObj = Serializer.deserializeProtocol(result, DatasetRequestMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
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
