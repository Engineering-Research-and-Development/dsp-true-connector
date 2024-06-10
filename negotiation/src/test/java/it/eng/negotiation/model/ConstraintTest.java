package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ConstraintTest {

	@Test
	public void equalsTrue() {
		assertTrue(ModelUtil.CONSTRAINT.equals(ModelUtil.CONSTRAINT));
	}
	
	@Test
	public void equalsFalse() {
		Constraint constraintA = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.DATE_TIME)
				.operator(Operator.GT)
				.rightOperand("2024-02-29T00:00:01+01:00")
				.build();
		Constraint constraintB = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.DATE_TIME)
				.operator(Operator.EQ)
				.rightOperand("2024-02-29T00:00:01+01:00")
				.build();
		assertFalse(constraintA.equals(constraintB));
		
	}
}

