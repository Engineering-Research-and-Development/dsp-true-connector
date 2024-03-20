package it.eng.catalog.transformer.to;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.tools.model.DSpaceConstants;

public class JsonToCatalogRequestMessageTransformTest {

	JsonToCatalogRequestMessageTransform transformer = new JsonToCatalogRequestMessageTransform();
	ObjectMapper mapper = new ObjectMapper();

	@Test
	public void toCatalogRequestMessage() {
		String filter = "catalogId=" + UUID.randomUUID().toString();
		Map<String, Object> map = new HashMap<>();
		map.put(DSpaceConstants.CONTEXT, DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE);
		map.put(DSpaceConstants.TYPE, DSpaceConstants.CATALOG_REQUEST_MESSAGE);
		map.put(DSpaceConstants.FILTER, Arrays.asList(filter));
		
		JsonNode jsonNode = mapper.valueToTree(map);
		
		var message = transformer.transform(jsonNode);
		
		assertNotNull(message);
		assertEquals(filter, message.getFilter().get(0));
	}
	
	@Test
	public void toCatalogRequestMessage_NoFilter() {
		Map<String, String> map = new HashMap<>();
		map.put(DSpaceConstants.CONTEXT, DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE);
		map.put(DSpaceConstants.TYPE, DSpaceConstants.CATALOG_REQUEST_MESSAGE);
		
		JsonNode jsonNode = mapper.valueToTree(map);
		
		var message = transformer.transform(jsonNode);
		
		assertNotNull(message);
		assertNull(message.getFilter());
	}
}
