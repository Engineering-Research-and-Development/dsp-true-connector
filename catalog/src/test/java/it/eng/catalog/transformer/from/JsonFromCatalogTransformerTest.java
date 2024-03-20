package it.eng.catalog.transformer.from;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import it.eng.catalog.util.MockObjectUtil;
import it.eng.tools.model.DSpaceConstants;

public class JsonFromCatalogTransformerTest {

	private JsonFromCatalogTransformer transformer = new JsonFromCatalogTransformer();
	
	@Test
	public void transform() {
		var jsonNode = transformer.transform(MockObjectUtil.createCatalog());
		assertNotNull(jsonNode, "Catalog must be null");
		assertEquals(DSpaceConstants.DSPACE + transformer.getInputType().getSimpleName(), jsonNode.get(DSpaceConstants.TYPE).asText());
		assertNotNull(jsonNode.get(DSpaceConstants.ID), "Id must be present");
		assertEquals(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE, jsonNode.get(DSpaceConstants.CONTEXT).asText());
		
		var dataset = jsonNode.get(DSpaceConstants.DATASET);
		assertNotNull(dataset.get(0), "Dataset cannot be null");
		
		var dataservice = jsonNode.get(DSpaceConstants.DATA_SERVICE);
		assertNotNull(dataservice.get(0), "Dataservice cannot be null");
	}
}
