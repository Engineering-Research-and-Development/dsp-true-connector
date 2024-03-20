package it.eng.catalog.mapper;

import org.junit.jupiter.api.Test;

import it.eng.catalog.entity.CatalogEntity;
import it.eng.catalog.entity.Resource;

public class CatalogMapperTest {
	
	private CatalogMapper mapper = new CatalogMapper();
	
	
	@Test
	public void catalogEntityToModelMappingTest() {
		
		CatalogEntity entity = new CatalogEntity();
		entity.getId();
	}

}
