package it.eng.catalog.mapper;

import it.eng.catalog.entity.MultilanguageEntity;
import it.eng.catalog.model.Multilanguage;

public class MultilanguageMapper implements MapperInterface<MultilanguageEntity, Multilanguage> {

	@Override
	public Multilanguage entityToModel(MultilanguageEntity entity) {
		return Multilanguage.Builder.newInstance()
				.language(entity.getLanguage())
				.value(entity.getValue())
				.build();
	}

	@Override
	public MultilanguageEntity modelToEntity(Multilanguage model) {
		// TODO Auto-generated method stub
		return null;
	}

}
