package it.eng.catalog.mapper;

import it.eng.catalog.entity.CatalogEntity;
import it.eng.catalog.model.Catalog;
import it.eng.tools.util.CSVUtil;

public class CatalogMapper implements MapperInterface<CatalogEntity, Catalog> {

	@Override
	public Catalog entityToModel(CatalogEntity entity) {
		return Catalog.Builder.newInstance()
				.id(entity.getId())
				.conformsTo(entity.getConformsTo())
				.creator(entity.getCreator())
				.dataset(entity.getDataset().stream().map(new DatasetMapper()::entityToModel).toList())
				.description(entity.getDescription().stream().map(new MultilanguageMapper()::entityToModel).toList())
				.distribution(entity.getDistribution().stream().map(new DistributionMapper()::entityToModel).toList())
				.homepage(entity.getHomepage())
				.identifier(entity.getIdentifier())
				.issued(entity.getIssued())
				.keyword(CSVUtil.toListString(entity.getKeyword()))
				.modified(entity.getModified())
				.participantId(entity.getParticipantId())
				.service(entity.getService().stream().map(new DataServiceMapper()::entityToModel).toList())
				.theme(CSVUtil.toListString(entity.getTheme()))
				.title(entity.getTitle())
				.build();
	}

	@Override
	public CatalogEntity modelToEntity(Catalog model) {
		// TODO Auto-generated method stub
		return null;
	}

}
