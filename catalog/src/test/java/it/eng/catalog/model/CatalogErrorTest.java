package it.eng.catalog.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;

public class CatalogErrorTest {

	CatalogError catalogError = CatalogError.Builder.newInstance()
			.code("cat err code")
			.reason(Arrays.asList(
					Reason.Builder.newInstance()
						.language("en")
						.value("Not correct")
					.build(),
					Reason.Builder.newInstance()
						.language("it")
						.value("same but in Italian")
					.build()))
			.build();
	
	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(catalogError);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.CODE));
		assertTrue(result.contains(DSpaceConstants.REASON));
		
		CatalogError javaObj = Serializer.deserializePlain(result, CatalogError.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	public void testPlain_protocol() {
		String result = Serializer.serializeProtocol(catalogError);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_CODE));
		assertTrue(result.contains(DSpaceConstants.DSPACE_REASON));
		
		CatalogError javaObj = Serializer.deserializeProtocol(result, CatalogError.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("no required fields")
	public void validateInvalid() {
		assertDoesNotThrow(() -> CatalogError.Builder.newInstance()
					.build());
	}

	private void validateJavaObj(CatalogError javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getCode());
		// must be exact one in array
		assertNotNull(javaObj.getReason().get(0));
	}
}
