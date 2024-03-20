package it.eng.catalog.transformer.from;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import it.eng.catalog.model.DataService;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.model.Reference;
import it.eng.tools.model.DSpaceConstants;

public class JsonFromDistributionTransformTest {

	private JsonFromDistributionTransform transformer = new JsonFromDistributionTransform();
	
	@Test
	public void transform() {
		DataService dataService = DataService.Builder.newInstance()
				.endpointURL("http://endpoint.url.test")
				.build();
		Distribution distribution = Distribution.Builder.newInstance()
				.dataService(Arrays.asList(dataService))
				.format(Reference.Builder.newInstance().id("dataServiceFormatTest").build())
				.build();
		
		var jsonNode = transformer.transform(distribution);
		
		assertNotNull(jsonNode, "Distribution shoud not be null");
//		assertEquals("dataServiceFormatTest", jsonNode.get(DSpaceConstants.DCT_FORMAT).asText());
		assertEquals(DSpaceConstants.DSPACE + transformer.getInputType().getSimpleName(), jsonNode.get(DSpaceConstants.TYPE).asText());
		assertNotNull(jsonNode.get(DSpaceConstants.ACCESS_SERVICE), "DataService ID should not be null");
	}
	
}
