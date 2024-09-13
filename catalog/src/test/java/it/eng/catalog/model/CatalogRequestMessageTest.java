package it.eng.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.serializer.Serializer;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class CatalogRequestMessageTest {

	private CatalogRequestMessage catalogRequestMessage = CatalogRequestMessage.Builder.newInstance()
			.filter(Arrays.asList("filter1"))
			.build();
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = Serializer.serializePlain(catalogRequestMessage);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.FILTER));
		
		CatalogRequestMessage javaObj = Serializer.deserializePlain(result, CatalogRequestMessage.class);
		validateJavaObj(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testPlain_protocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(catalogRequestMessage);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DSPACE_FILTER).asText());
		
		CatalogRequestMessage javaObj = Serializer.deserializeProtocol(result, CatalogRequestMessage.class);
		validateJavaObj(javaObj);
	}
	
	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(catalogRequestMessage);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, CatalogRequestMessage.class));
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertDoesNotThrow(() -> CatalogRequestMessage.Builder.newInstance()
					.build());
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = Serializer.serializePlain(catalogRequestMessage);
		CatalogRequestMessage catalogRequestMessage2 = Serializer.deserializePlain(ss, CatalogRequestMessage.class);
		assertThat(catalogRequestMessage).usingRecursiveComparison().isEqualTo(catalogRequestMessage2);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		String ss = Serializer.serializeProtocol(catalogRequestMessage);
		CatalogRequestMessage catalogRequestMessage2 = Serializer.deserializeProtocol(ss, CatalogRequestMessage.class);
		assertThat(catalogRequestMessage).usingRecursiveComparison().isEqualTo(catalogRequestMessage2);
	}
	
	private void validateJavaObj(CatalogRequestMessage javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getFilter());
		// must be exact one in array
		assertNotNull(javaObj.getFilter().get(0));
	}
	
}
