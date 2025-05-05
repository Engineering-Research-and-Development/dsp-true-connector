package it.eng.catalog.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

import it.eng.catalog.model.DataService;

public class DataServiceUtil {

	public static final DataService DATA_SERVICE = DataService.Builder.newInstance()
			.id(UUID.randomUUID().toString())
			.keyword(Arrays.asList("DataService keyword1", "DataService keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
			.theme(Arrays.asList("DataService theme1", "DataService theme2").stream().collect(Collectors.toCollection(HashSet::new)))
			.conformsTo(CatalogMockObjectUtil.CONFORMSTO)
			.creator(CatalogMockObjectUtil.CREATOR)
			.description(Arrays.asList(CatalogMockObjectUtil.MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
			.identifier(CatalogMockObjectUtil.IDENTIFIER)
			.issued(CatalogMockObjectUtil.ISSUED)
			.modified(CatalogMockObjectUtil.MODIFIED)
			.title(CatalogMockObjectUtil.TITLE)
			.endpointURL("http://dataservice.com")
			.endpointDescription("endpoint description")
			.build();
	
	public static final DataService DATA_SERVICE_UPDATE = DataService.Builder.newInstance()
			.id(UUID.randomUUID().toString())
			.keyword(Arrays.asList("DataService keyword1 update", "DataService keyword2 update").stream().collect(Collectors.toCollection(HashSet::new)))
			.theme(Arrays.asList("DataService theme1 update").stream().collect(Collectors.toCollection(HashSet::new)))
			.conformsTo(CatalogMockObjectUtil.CONFORMSTO)
			.creator(CatalogMockObjectUtil.CREATOR + " update")
			.description(Arrays.asList(CatalogMockObjectUtil.MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
			.identifier(CatalogMockObjectUtil.IDENTIFIER)
			.issued(CatalogMockObjectUtil.ISSUED)
			.modified(CatalogMockObjectUtil.MODIFIED)
			.title(CatalogMockObjectUtil.TITLE + " update")
			.endpointURL("http://dataservice.com/update")
			.endpointDescription("endpoint description update")
			.build();
}
