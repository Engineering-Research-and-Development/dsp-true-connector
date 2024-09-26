package it.eng.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.catalog.serializer.Serializer;

public class ConstraintTest {

	private Constraint constraintA = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.DATE_TIME)
			.operator(Operator.GT)
			.rightOperand("2024-02-29T00:00:01+01:00")
			.build();
	
	private Constraint constraintB = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.DATE_TIME)
			.operator(Operator.EQ)
			.rightOperand("2024-02-29T00:00:01+01:00")
			.build();
	
	@Test
	public void equalsTrue() {
		assertTrue(constraintA.equals(constraintA));
	}

	@Test
	public void equalsFalse() {
		assertFalse(constraintA.equals(constraintB));
	}
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = Serializer.serializePlain(constraintA);
		Constraint constraintA2 = Serializer.deserializePlain(ss, Constraint.class);
		assertThat(constraintA).usingRecursiveComparison().usingOverriddenEquals().isEqualTo(constraintA2);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		String ss = Serializer.serializeProtocol(constraintA);
		Constraint constraintA2 = Serializer.deserializeProtocol(ss, Constraint.class);
		assertThat(constraintA).usingRecursiveComparison().usingOverriddenEquals().isEqualTo(constraintA2);
	}
}
