package it.eng.catalog.transformer.from;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.tools.model.DSpaceConstants;

public class JsonFromCatalogRequestMessageTest {

	private JsonFromCatalogRequestMessage transformer = new JsonFromCatalogRequestMessage();
	
	@Test
	public void transformToJson() {
		CatalogRequestMessage message = CatalogRequestMessage.Builder.newInstance().filter(Arrays.asList("some filter")).build();
		
		var jsonNode = transformer.transform(message);
		
		assertNotNull(jsonNode);
		assertEquals(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE, jsonNode.get(DSpaceConstants.CONTEXT).asText());
		assertEquals(DSpaceConstants.DSPACE + CatalogRequestMessage.class.getSimpleName(), jsonNode.get(DSpaceConstants.TYPE).asText());
		assertEquals("some filter", jsonNode.get(DSpaceConstants.FILTER).get(0).asText());
	}
	
	@Test
	public void transformToJson_NoFilter() {
		CatalogRequestMessage message = CatalogRequestMessage.Builder.newInstance().build();
		
		var jsonNode = transformer.transform(message);
		
		assertNotNull(jsonNode);
		assertEquals(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE, jsonNode.get(DSpaceConstants.CONTEXT).asText());
		assertEquals(DSpaceConstants.DSPACE + transformer.getInputType().getSimpleName(), jsonNode.get(DSpaceConstants.TYPE).asText());
		assertNull(jsonNode.get(DSpaceConstants.FILTER).get(0));
	}
}
