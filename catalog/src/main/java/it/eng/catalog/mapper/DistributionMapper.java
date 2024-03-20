package it.eng.catalog.mapper;

import it.eng.catalog.entity.DistributionEntity;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.model.Reference;

public class DistributionMapper implements MapperInterface<DistributionEntity, Distribution> {

	@Override
	public Distribution entityToModel(DistributionEntity entity) {
		return Distribution.Builder.newInstance()
				.dataService(entity.getAccessServices().stream().map(new DataServiceMapper()::entityToModel).toList())
				.description(entity.getDescriptions().stream().map(new MultilanguageMapper()::entityToModel).toList())
				.format(Reference.Builder.newInstance().id(entity.getFormat()).build())
				.hasPolicy(entity.getHasPolicies().stream().map(new OfferMapper()::entityToModel).toList())
				.issued(entity.getIssued())
				.modified(entity.getModified())
				.title(entity.getTitle())
				.build();
	}

	@Override
	public DistributionEntity modelToEntity(Distribution model) {
		// TODO Auto-generated method stub
		return null;
	}

}
