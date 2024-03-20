package it.eng.catalog.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;

public class CatalogRequestMessageTest {

	private CatalogRequestMessage catalogRequestMessage = CatalogRequestMessage.Builder.newInstance()
			.filter(Arrays.asList("filter1"))
			.build();
	
	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(catalogRequestMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.FILTER));
		
		CatalogRequestMessage javaObj = Serializer.deserializePlain(result, CatalogRequestMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	public void testPlain_protocol() {
		String result = Serializer.serializeProtocol(catalogRequestMessage);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_FILTER));
		
		CatalogRequestMessage javaObj = Serializer.deserializeProtocol(result, CatalogRequestMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("no required fields")
	public void validateInvalid() {
		assertDoesNotThrow(() -> CatalogRequestMessage.Builder.newInstance()
					.build());
	}
	
	private void validateJavaObj(CatalogRequestMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getFilter());
		// must be exact one in array
		assertNotNull(javaObj.getFilter().get(0));
	}
	
}
