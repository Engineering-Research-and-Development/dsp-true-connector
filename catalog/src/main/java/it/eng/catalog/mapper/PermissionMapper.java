package it.eng.catalog.mapper;

import it.eng.catalog.entity.PermissionEntity;
import it.eng.catalog.model.Action;
import it.eng.catalog.model.Permission;

public class PermissionMapper implements MapperInterface<PermissionEntity, Permission> {

	@Override
	public Permission entityToModel(PermissionEntity entity) {
		return Permission.Builder.newInstance()
				.action(Action.getEnum(entity.getAction()))
				.assignee(entity.getAssignee())
				.assigner(entity.getAssigner())
				.constraint(entity.getConstraint().stream().map(new ConstraintMapper()::entityToModel).toList())
				.target(entity.getTarget())
				.build();
	}

	@Override
	public PermissionEntity modelToEntity(Permission model) {
		// TODO Auto-generated method stub
		return null;
	}


}
