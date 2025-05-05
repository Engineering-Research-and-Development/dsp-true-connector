package it.eng.catalog.model;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class CatalogTest {
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = CatalogSerializer.serializePlain(CatalogMockObjectUtil.CATALOG);
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
		assertTrue(result.contains(DSpaceConstants.TITLE));
		
		assertTrue(result.contains(DSpaceConstants.DISTRIBUTION));
		
		Catalog javaObj = CatalogSerializer.deserializePlain(result, Catalog.class);
		
		Dataset dataset = javaObj.getDataset().iterator().next();
		validateDataset(dataset);
	}
	
	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = CatalogSerializer.serializeProtocolJsonNode(CatalogMockObjectUtil.CATALOG);
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
		assertNotNull(result.get(DSpaceConstants.DCAT_DISTRIBUTION).asText());
		
		Catalog javaObj = CatalogSerializer.deserializeProtocol(result, Catalog.class);
		validateDataset(javaObj.getDataset().iterator().next());
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = CatalogSerializer.serializePlainJsonNode(CatalogMockObjectUtil.CATALOG);
		assertThrows(ValidationException.class, () -> CatalogSerializer.deserializeProtocol(result, Catalog.class));
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertDoesNotThrow(() -> Catalog.Builder.newInstance()
					.build());
	}
	
	@Test
	public void findOffer() {
		boolean offerExists = CatalogMockObjectUtil.CATALOG.getDataset().stream()
				.flatMap(dataset -> dataset.getHasPolicy().stream()).anyMatch(of -> of.getId().equals("urn:offer_id"));
		assertTrue(offerExists);

		offerExists = CatalogMockObjectUtil.CATALOG.getDataset().stream()
				.flatMap(dataset -> dataset.getHasPolicy().stream()).anyMatch(of -> of.getId().equals("urn:offer_id_not_found"));
		assertFalse(offerExists);
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		Catalog catalog = CatalogMockObjectUtil.CATALOG;
		String ss = CatalogSerializer.serializePlain(catalog);
		Catalog catalog2 = CatalogSerializer.deserializePlain(ss, Catalog.class);
		assertThat(catalog).usingRecursiveComparison().isEqualTo(catalog2);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		Catalog catalog = CatalogMockObjectUtil.CATALOG;
		String ss = CatalogSerializer.serializeProtocol(catalog);
		Catalog catalog2 = CatalogSerializer.deserializeProtocol(ss, Catalog.class);
		assertThat(catalog).usingRecursiveComparison().isEqualTo(catalog2);
	}
	
	public void validateDataset(Dataset javaObj) {
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
