package it.eng.catalog.transformer.from;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import it.eng.catalog.model.DataService;

public class JsonFromDataServiceTransformTest {

	private JsonFromDataServiceTransform transformer = new JsonFromDataServiceTransform();
	
	@Test
	public void transform() {
		var jsonNode = transformer.transform(DataService.Builder.newInstance()
				.endpointURL("https://dataservice.endpoint.test")
				.build());
		
		assertNotNull(jsonNode, "Data Service must be present");
	}
}
