package it.eng.catalog.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.eng.catalog.serializer.Serializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.util.MockObjectUtil;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class DistributionTest {
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = Serializer.serializePlain(MockObjectUtil.DISTRIBUTION);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.TITLE));
		assertTrue(result.contains(DSpaceConstants.DESCRIPTION));
		assertTrue(result.contains(DSpaceConstants.ISSUED));
		assertTrue(result.contains(DSpaceConstants.MODIFIED));
		assertTrue(result.contains(DSpaceConstants.ACCESS_SERVICE));
		
		Distribution javaObj = Serializer.deserializePlain(result, Distribution.class);
		validateDistribution(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(MockObjectUtil.DISTRIBUTION);
		assertNull(result.get(DSpaceConstants.CONTEXT), "Not root element to have context");
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_TITLE).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_DESCRIPTION).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_MODIFIED).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_ISSUED).asText());
		assertNotNull(result.get(DSpaceConstants.DCAT_ACCESS_SERVICE).asText());
		
		Distribution javaObj = Serializer.deserializeProtocol(result, Distribution.class);
		validateDistribution(javaObj);
	}

	@Test
	@DisplayName("Missing @context and @type")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(MockObjectUtil.DISTRIBUTION);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, Distribution.class));
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class,
				() -> Distribution.Builder.newInstance()
					.build());
	}
	
	private void validateDistribution(Distribution distribution) {
		assertNotNull(distribution.getTitle());
		assertNotNull(distribution.getAccessService());
		assertNotNull(distribution.getDescription());
		assertNotNull(distribution.getHasPolicy());
		assertNotNull(distribution.getIssued());
		assertNotNull(distribution.getModified());
	}
}
