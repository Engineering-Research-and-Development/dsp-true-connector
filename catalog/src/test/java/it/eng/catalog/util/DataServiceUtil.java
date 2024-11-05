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
			.theme(Arrays.asList("DataServiceTheme1", "DataServiceTheme2").stream().collect(Collectors.toCollection(HashSet::new)))
			.conformsTo(MockObjectUtil.CONFORMSTO)
			.creator(MockObjectUtil.CREATOR)
			.description(Arrays.asList(MockObjectUtil.MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
			.identifier(MockObjectUtil.IDENTIFIER)
			.issued(MockObjectUtil.ISSUED)
			.modified(MockObjectUtil.MODIFIED)
			.title(MockObjectUtil.TITLE)
			.endpointURL("http://dataservice.com")
			.endpointDescription("endpoint description")
			.build();
	
	public static final DataService DATA_SERVICE_UPDATE = DataService.Builder.newInstance()
			.id(UUID.randomUUID().toString())
			.keyword(Arrays.asList("DataService keyword1 update", "DataService keyword2 update").stream().collect(Collectors.toCollection(HashSet::new)))
			.theme(Arrays.asList("DataService theme1 update").stream().collect(Collectors.toCollection(HashSet::new)))
			.conformsTo(MockObjectUtil.CONFORMSTO)
			.creator(MockObjectUtil.CREATOR + " update")
			.description(Arrays.asList(MockObjectUtil.MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
			.identifier(MockObjectUtil.IDENTIFIER)
			.issued(MockObjectUtil.ISSUED)
			.modified(MockObjectUtil.MODIFIED)
			.title(MockObjectUtil.TITLE + " update")
			.endpointURL("http://dataservice.com/update")
			.endpointDescription("endpoint description update")
			.build();
}
