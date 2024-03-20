package it.eng.catalog.transformer.from;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import it.eng.catalog.entity.OfferEntity;
import it.eng.catalog.model.CatalogError;
import it.eng.catalog.model.Reason;
import it.eng.tools.model.DSpaceConstants;

public class JsonFromCatalogErrorTransformerTest {

	private JsonFromCatalogErrorTransformer transformer = new JsonFromCatalogErrorTransformer();
	
	@Test
	public void transformToJson( ) {
		var jsonNode = transformer.transform(CatalogError.Builder.newInstance()
				.code("123")
				.reason(Arrays.asList(Reason.Builder.newInstance().value("Chuck Norris occured").language("en").build()))
				.build());
	
		assertNotNull(jsonNode);

		assertEquals(DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE, jsonNode.get(DSpaceConstants.CONTEXT).asText());
		assertEquals(DSpaceConstants.DSPACE + CatalogError.class.getSimpleName(), jsonNode.get(DSpaceConstants.TYPE).asText());
		assertEquals("123", jsonNode.get(DSpaceConstants.CODE).asText());
		var reason = jsonNode.get(DSpaceConstants.REASON);
		assertNotNull(reason, "Reason should be present");
		assertEquals("Chuck Norris occured", reason.get(0).get(DSpaceConstants.VALUE).asText());
		OfferEntity oe = new OfferEntity();

	}
}
