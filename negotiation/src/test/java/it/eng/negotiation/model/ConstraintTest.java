package it.eng.negotiation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.negotiation.serializer.NegotiationSerializer;

public class ConstraintTest {

	@Test
	public void equalsTrue() {
		assertTrue(NegotiationMockObjectUtil.CONSTRAINT.equals(NegotiationMockObjectUtil.CONSTRAINT));
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
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		Constraint constraint = NegotiationMockObjectUtil.CONSTRAINT;
		String ss = NegotiationSerializer.serializePlain(constraint);
		Constraint obj = NegotiationSerializer.deserializePlain(ss, Constraint.class);
		assertThat(constraint).usingRecursiveComparison().isEqualTo(obj);
	}
	
	@Test
	@DisplayName("Protocol serialize/deserialize")
	public void equalsTestProtocol() {
		Constraint constraint = NegotiationMockObjectUtil.CONSTRAINT;
		String ss = NegotiationSerializer.serializeProtocol(constraint);
		Constraint obj = NegotiationSerializer.deserializeProtocol(ss, Constraint.class);
		assertThat(constraint).usingRecursiveComparison().isEqualTo(obj);
	}
}

