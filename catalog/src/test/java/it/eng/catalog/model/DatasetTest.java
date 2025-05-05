package it.eng.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class DatasetTest {

	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = CatalogSerializer.serializePlain(CatalogMockObjectUtil.DATASET);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.KEYWORD));
		assertTrue(result.contains(DSpaceConstants.THEME));
		assertTrue(result.contains(DSpaceConstants.CONFORMSTO));
		
		assertTrue(result.contains(DSpaceConstants.CREATOR));
		assertTrue(result.contains(DSpaceConstants.DESCRIPTION));
		assertTrue(result.contains(DSpaceConstants.IDENTIFIER));
		assertTrue(result.contains(DSpaceConstants.ISSUED));
		assertTrue(result.contains(DSpaceConstants.MODIFIED));
		assertTrue(result.contains(DSpaceConstants.MODIFIED));
		assertTrue(result.contains(DSpaceConstants.DISTRIBUTION));
		
		Dataset javaObj = CatalogSerializer.deserializePlain(result, Dataset.class);
		validateDataset(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = CatalogSerializer.serializeProtocolJsonNode(CatalogMockObjectUtil.DATASET);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DCAT_KEYWORD).asText());
		assertNotNull(result.get(DSpaceConstants.DCAT_THEME).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_CONFORMSTO).asText());
		
		assertNotNull(result.get(DSpaceConstants.DCT_CREATOR).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_DESCRIPTION).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_IDENTIFIER).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_ISSUED).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_MODIFIED).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_MODIFIED).asText());
		assertNotNull(result.get(DSpaceConstants.DCAT_DISTRIBUTION).asText());
		
		Dataset javaObj = CatalogSerializer.deserializeProtocol(result, Dataset.class);
		validateDataset(javaObj);
	}

	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = CatalogSerializer.serializePlainJsonNode(CatalogMockObjectUtil.DATASET);
		assertThrows(ValidationException.class, () -> CatalogSerializer.deserializeProtocol(result, Dataset.class));
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class,
				() -> Dataset.Builder.newInstance()
					.build());
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		Dataset dataset = CatalogMockObjectUtil.DATASET;
		String ss = CatalogSerializer.serializePlain(dataset);
		Dataset dataset2 = CatalogSerializer.deserializePlain(ss, Dataset.class);
		assertThat(dataset).usingRecursiveComparison().isEqualTo(dataset2);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		Dataset dataset = CatalogMockObjectUtil.DATASET;
		String ss = CatalogSerializer.serializeProtocol(dataset);
		Dataset dataset2 = CatalogSerializer.deserializeProtocol(ss, Dataset.class);
		assertThat(dataset).usingRecursiveComparison().isEqualTo(dataset2);
	}
	
	private void validateDataset(Dataset javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConformsTo());
		assertNotNull(javaObj.getCreator());
		assertNotNull(javaObj.getDescription().iterator().next());
		assertNotNull(javaObj.getDistribution().iterator().next());
		assertNotNull(javaObj.getIdentifier());
		assertNotNull(javaObj.getIssued());
		assertNotNull(javaObj.getKeyword());
		assertEquals(2, javaObj.getKeyword().size());
		assertEquals(3, javaObj.getTheme().size());
		assertNotNull(javaObj.getModified());
		assertNotNull(javaObj.getTheme());
		assertNotNull(javaObj.getTitle());
	}
}
