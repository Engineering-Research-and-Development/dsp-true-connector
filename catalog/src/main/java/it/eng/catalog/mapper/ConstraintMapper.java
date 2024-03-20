package it.eng.catalog.mapper;

import it.eng.catalog.entity.ConstraintEntity;
import it.eng.catalog.model.Constraint;
import it.eng.catalog.model.LeftOperand;
import it.eng.catalog.model.Operator;

public class ConstraintMapper implements MapperInterface<ConstraintEntity, Constraint> {

	@Override
	public Constraint entityToModel(ConstraintEntity entity) {
		return Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.getEnum(entity.getLeftOperand()))
				.rightOperand(entity.getRightOperand())
				.operator(Operator.getEnum(entity.getOperator()))
				.build();
	}

	@Override
	public ConstraintEntity modelToEntity(Constraint model) {
		// TODO Auto-generated method stub
		return null;
	}

}
