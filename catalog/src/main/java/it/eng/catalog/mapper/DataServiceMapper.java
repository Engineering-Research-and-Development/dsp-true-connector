package it.eng.catalog.mapper;

import it.eng.catalog.entity.DataServiceEntity;
import it.eng.catalog.model.DataService;
import it.eng.tools.util.CSVUtil;

public class DataServiceMapper implements MapperInterface<DataServiceEntity, DataService> {

	@Override
	public DataService entityToModel(DataServiceEntity entity) {
		return DataService.Builder.newInstance()
				.id(entity.getId())
				.conformsTo(entity.getConformsTo())
				.creator(entity.getCreator())
				.description(entity.getDescription().stream().map(new MultilanguageMapper()::entityToModel).toList())
				.endpointDescription(entity.getEndpointDescription())
				.endpointURL(entity.getEndpointURL())
				.identifier(entity.getIdentifier())
				.issued(entity.getIssued())
				.keyword(CSVUtil.toListString(entity.getKeyword()))
				.modified(entity.getModified())
				.theme(CSVUtil.toListString(entity.getTheme()))
				.title(entity.getTitle())
				.build();
	}

	@Override
	public DataServiceEntity modelToEntity(DataService model) {
		// TODO Auto-generated method stub
		return null;
	}

}
