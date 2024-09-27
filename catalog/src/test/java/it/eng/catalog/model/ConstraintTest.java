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
	
	@Test
	public void compareConstraints() {
		Constraint CONSTRAINT_AS_OBJECT = Constraint.Builder.newInstance()
				.leftOperand(Reference.Builder.newInstance().id(LeftOperand.DATE_TIME.toString()).build())
				.operator(Reference.Builder.newInstance().id(Operator.GT.toString()).build())
				.rightOperand("2024-02-29T00:00:01+01:00")
				.build();
		Constraint CONSTRAINT = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.DATE_TIME)
				.operator(Operator.GT)
				.rightOperand("2024-02-29T00:00:01+01:00")
				.build();
		
		Constraint CONSTRAINT_STRING = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.DATE_TIME.toString())
				.operator(Operator.GT.toString())
				.rightOperand("2024-02-29T00:00:01+01:00")
				.build();
		
		Constraint CONSTRAINT_LEFT_OPERAND_OBJ = Constraint.Builder.newInstance()
				.leftOperand(Reference.Builder.newInstance().id(LeftOperand.DATE_TIME.toString()).build())
				.operator(Operator.GT)
				.rightOperand("2024-02-29T00:00:01+01:00")
				.build();
		Constraint CONSTRAINT_OPERATOR_OBJ = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.DATE_TIME)
				.operator(Reference.Builder.newInstance().id(Operator.GT.toString()).build())
				.rightOperand("2024-02-29T00:00:01+01:00")
				.build();
		
		assertTrue(CONSTRAINT_AS_OBJECT.equals(CONSTRAINT_STRING));
		
		assertTrue(CONSTRAINT_AS_OBJECT.equals(CONSTRAINT));
		assertTrue(CONSTRAINT_AS_OBJECT.equals(CONSTRAINT_LEFT_OPERAND_OBJ));
		assertTrue(CONSTRAINT_AS_OBJECT.equals(CONSTRAINT_OPERATOR_OBJ));
		
		assertTrue(CONSTRAINT.equals(CONSTRAINT_LEFT_OPERAND_OBJ));
		assertTrue(CONSTRAINT.equals(CONSTRAINT_OPERATOR_OBJ));
		
		assertTrue(CONSTRAINT_LEFT_OPERAND_OBJ.equals(CONSTRAINT_OPERATOR_OBJ));
		
		Constraint afterSerializationProtocol = Serializer.deserializeProtocol(Serializer.serializeProtocol(CONSTRAINT_AS_OBJECT), Constraint.class);
		assertTrue(afterSerializationProtocol.equals(CONSTRAINT_AS_OBJECT));
		assertTrue(afterSerializationProtocol.equals(CONSTRAINT_LEFT_OPERAND_OBJ));
		assertTrue(afterSerializationProtocol.equals(CONSTRAINT_OPERATOR_OBJ));
		
		Constraint afterSerializationProtocol2 = Serializer.deserializeProtocol(Serializer.serializeProtocol(CONSTRAINT_OPERATOR_OBJ), Constraint.class);
		assertTrue(afterSerializationProtocol2.equals(CONSTRAINT_AS_OBJECT));
		assertTrue(afterSerializationProtocol2.equals(CONSTRAINT_LEFT_OPERAND_OBJ));
		assertTrue(afterSerializationProtocol2.equals(CONSTRAINT_OPERATOR_OBJ));
		
		Constraint afterSerializationProtocol3 = Serializer.deserializeProtocol(Serializer.serializeProtocol(CONSTRAINT_LEFT_OPERAND_OBJ), Constraint.class);
		assertTrue(afterSerializationProtocol3.equals(CONSTRAINT_AS_OBJECT));
		assertTrue(afterSerializationProtocol3.equals(CONSTRAINT_LEFT_OPERAND_OBJ));
		assertTrue(afterSerializationProtocol3.equals(CONSTRAINT_OPERATOR_OBJ));
	
		Constraint afterSerializationPlain = Serializer.deserializePlain(Serializer.serializePlain(CONSTRAINT_AS_OBJECT), Constraint.class);
		assertTrue(afterSerializationPlain.equals(CONSTRAINT_AS_OBJECT));
		assertTrue(afterSerializationPlain.equals(CONSTRAINT_LEFT_OPERAND_OBJ));
		assertTrue(afterSerializationPlain.equals(CONSTRAINT_OPERATOR_OBJ));
	}
}
