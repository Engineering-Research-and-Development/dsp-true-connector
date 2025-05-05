package it.eng.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.util.DataServiceUtil;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class DataServiceTest {

	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = CatalogSerializer.serializePlain(DataServiceUtil.DATA_SERVICE);
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
		assertTrue(result.contains(DSpaceConstants.TITLE));
		
		assertTrue(result.contains(DSpaceConstants.ENDPOINT_URL));
		assertTrue(result.contains(DSpaceConstants.ENDPOINT_DESCRIPTION));
		
		DataService javaObj = CatalogSerializer.deserializePlain(result, DataService.class);
		validateDataService(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = CatalogSerializer.serializeProtocolJsonNode(DataServiceUtil.DATA_SERVICE);
		assertNull(result.get(DSpaceConstants.CONTEXT), "Not root element to have context");
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());

		assertNotNull(result.get(DSpaceConstants.ID).asText());
		assertNotNull(result.get(DSpaceConstants.DCAT_KEYWORD).asText());
		assertNotNull(result.get(DSpaceConstants.DCAT_THEME).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_CONFORMSTO).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_CREATOR).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_DESCRIPTION).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_IDENTIFIER).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_TITLE).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_ISSUED).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_MODIFIED).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_MODIFIED).asText());
		
		assertNotNull(result.get(DSpaceConstants.DCAT_ENDPOINT_URL).asText());
		assertNotNull(result.get(DSpaceConstants.DCAT_ENDPOINT_DESCRIPTION).asText());
		
		DataService javaObj = CatalogSerializer.deserializeProtocol(result, DataService.class);
		validateDataService(javaObj);
	}

	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = CatalogSerializer.serializePlainJsonNode(DataServiceUtil.DATA_SERVICE);
		assertThrows(ValidationException.class, () -> CatalogSerializer.deserializeProtocol(result, DataService.class));
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertDoesNotThrow(() -> DataService.Builder.newInstance().build(), "No mandatory fields");
	}
	
	@Test
	@DisplayName("Update instance")
	public void updateInstance() {
		DataService dataServiceForUpdate = DataService.Builder.newInstance()
	            .keyword(Set.of("keyword1", "keyword2"))
	            .theme(Set.of("red", "green", "black"))
	            .creator("Updater")
	            .description(Set.of(CatalogMockObjectUtil.MULTILANGUAGE))
	            .endpointDescription("Description for test")
	            .endpointURL("updatedEndpointUrl")
	            .build();
		
		DataService updated = CatalogMockObjectUtil.DATA_SERVICE.updateInstance(dataServiceForUpdate);
		assertEquals("Updater", updated.getCreator());
		assertEquals(CatalogMockObjectUtil.CONFORMSTO, updated.getConformsTo());
		assertTrue(updated.getKeyword().contains("keyword1"));
		assertTrue(updated.getTheme().contains("red"));
		assertTrue(updated.getTheme().contains("green"));
		assertTrue(updated.getTheme().contains("black"));
		assertEquals("updatedEndpointUrl", updated.getEndpointURL());
		assertEquals("Description for test", updated.getEndpointDescription());
		assertEquals(CatalogMockObjectUtil.ISSUED, updated.getIssued());
		assertEquals(CatalogMockObjectUtil.TITLE, updated.getTitle());
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		DataService dataService = CatalogMockObjectUtil.DATA_SERVICE;
		String ss = CatalogSerializer.serializePlain(dataService);
		DataService dataService2 = CatalogSerializer.deserializePlain(ss, DataService.class);
		assertThat(dataService).usingRecursiveComparison().isEqualTo(dataService2);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		DataService dataService = CatalogMockObjectUtil.DATA_SERVICE;
		String ss = CatalogSerializer.serializeProtocol(dataService);
		DataService dataService2 = CatalogSerializer.deserializeProtocol(ss, DataService.class);
		assertThat(dataService).usingRecursiveComparison().isEqualTo(dataService2);
	}
	
	private void validateDataService(DataService dataService) {
		assertNotNull(dataService.getConformsTo());
		assertNotNull(dataService.getCreator());
		assertNotNull(dataService.getDescription().iterator().next());
		assertNotNull(dataService.getIdentifier());
		assertNotNull(dataService.getIssued());
		assertNotNull(dataService.getKeyword());
		assertEquals(2, dataService.getKeyword().size());
		assertEquals(2, dataService.getTheme().size());
		assertNotNull(dataService.getModified());
		assertNotNull(dataService.getTheme());
		assertNotNull(dataService.getTitle());
		
		assertNotNull(dataService.getEndpointDescription());
		assertNotNull(dataService.getEndpointURL());
	}
}
