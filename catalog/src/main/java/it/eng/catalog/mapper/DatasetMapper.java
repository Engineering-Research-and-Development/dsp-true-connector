package it.eng.catalog.mapper;

import it.eng.catalog.entity.DatasetEntity;
import it.eng.catalog.model.Dataset;
import it.eng.tools.util.CSVUtil;

public class DatasetMapper implements MapperInterface<DatasetEntity, Dataset>{

	@Override
	public Dataset entityToModel(DatasetEntity entity) {
		return Dataset.Builder.newInstance()
				.id(entity.getId())
				.conformsTo(entity.getConformsTo())
				.creator(entity.getCreator())
				.description(entity.getDescription().stream().map(new MultilanguageMapper()::entityToModel).toList())
				.distribution(entity.getDistribution().stream().map(new DistributionMapper()::entityToModel).toList())
				.hasPolicy(entity.getHasPolicy().stream().map(new OfferMapper()::entityToModel).toList())
				.identifier(entity.getIdentifier())
				.issued(entity.getIssued())
				.keyword(CSVUtil.toListString(entity.getKeyword()))
				.modified(entity.getModified())
				.theme(CSVUtil.toListString(entity.getTheme()))
				.title(entity.getTitle())
				.build();
	}

	@Override
	public DatasetEntity modelToEntity(Dataset model) {
		// TODO Auto-generated method stub
		return null;
	}

}
