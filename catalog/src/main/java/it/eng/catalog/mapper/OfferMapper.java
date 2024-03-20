package it.eng.catalog.mapper;

import it.eng.catalog.entity.OfferEntity;
import it.eng.catalog.model.Offer;

public class OfferMapper implements MapperInterface<OfferEntity, Offer> {

	@Override
	public Offer entityToModel(OfferEntity entity) {
		return Offer.Builder.newInstance()
				.id(entity.getId())
				.assignee(entity.getAssignee())
				.assigner(entity.getAssigner())
				.permission(entity.getPermissions().stream().map(new PermissionMapper()::entityToModel).toList())
				.target(entity.getTarget())
				.build();
	}

	@Override
	public OfferEntity modelToEntity(Offer model) {
		// TODO Auto-generated method stub
		return null;
	}

}
