package it.eng.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class DatasetRequestMessageTest {

	private DatasetRequestMessage datasetRequestMessage = DatasetRequestMessage.Builder.newInstance()
			.dataset("DATASET")
			.build();

	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = CatalogSerializer.serializePlain(datasetRequestMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DATASET));
		
		DatasetRequestMessage javaObj = CatalogSerializer.deserializePlain(result, DatasetRequestMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testPlain_protocol() {
		JsonNode result = CatalogSerializer.serializeProtocolJsonNode(datasetRequestMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_DATASET));
		
		DatasetRequestMessage javaObj = CatalogSerializer.deserializeProtocol(result, DatasetRequestMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = CatalogSerializer.serializePlainJsonNode(datasetRequestMessage);
		assertThrows(ValidationException.class, () -> CatalogSerializer.deserializeProtocol(result, DatasetRequestMessage.class));
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class,
				() -> DatasetRequestMessage.Builder.newInstance()
					.build());
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = CatalogSerializer.serializePlain(datasetRequestMessage);
		DatasetRequestMessage DatasetRequestMessage2 = CatalogSerializer.deserializePlain(ss, DatasetRequestMessage.class);
		assertThat(datasetRequestMessage).usingRecursiveComparison().isEqualTo(DatasetRequestMessage2);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		String ss = CatalogSerializer.serializeProtocol(datasetRequestMessage);
		DatasetRequestMessage DatasetRequestMessage2 = CatalogSerializer.deserializeProtocol(ss, DatasetRequestMessage.class);
		assertThat(datasetRequestMessage).usingRecursiveComparison().isEqualTo(DatasetRequestMessage2);
	}
	
	private void validateJavaObj(DatasetRequestMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getDataset());
	}
}
