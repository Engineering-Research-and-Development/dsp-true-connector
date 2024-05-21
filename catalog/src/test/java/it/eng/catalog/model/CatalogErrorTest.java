package it.eng.catalog.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import it.eng.catalog.serializer.Serializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

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
	@DisplayName("Verify valid plain object serialization")
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
	@DisplayName("Verify valid protocol object serialization")
	public void testPlain_protocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(catalogError);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_CODE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_REASON).asText());
		
		CatalogError javaObj = Serializer.deserializeProtocol(result, CatalogError.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(catalogError);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, CatalogError.class));
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
