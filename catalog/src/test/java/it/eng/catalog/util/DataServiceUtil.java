package it.eng.catalog.util;

import java.util.Arrays;
import java.util.UUID;

import it.eng.catalog.model.DataService;

public class DataServiceUtil {

	public static DataService DATA_SERVICE = DataService.Builder.newInstance()
			.id(UUID.randomUUID().toString())
			.keyword(Arrays.asList("DataService keyword1", "DataService keyword2"))
			.theme(Arrays.asList("DataService theme1", "DataService theme2"))
			.conformsTo(MockObjectUtil.CONFORMSTO)
			.creator(MockObjectUtil.CREATOR)
			.description(Arrays.asList(MockObjectUtil.MULTILANGUAGE))
			.identifier(MockObjectUtil.IDENTIFIER)
			.issued(MockObjectUtil.ISSUED)
			.modified(MockObjectUtil.MODIFIED)
			.title(MockObjectUtil.TITLE)
			.endpointURL("http://dataservice.com")
			.endpointDescription("endpoint description")
			.servesDataset(Arrays.asList(MockObjectUtil.DATASET))
			.build();
}
