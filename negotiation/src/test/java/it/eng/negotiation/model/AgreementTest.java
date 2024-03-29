package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import jakarta.validation.ValidationException;

public class AgreementTest {

	@Test
	public void validAgreement() {
		Agreement agreement = Agreement.Builder.newInstance()
				.id(ModelUtil.generateUUID())
				.assignee(ModelUtil.ASSIGNEE)
				.assigner(ModelUtil.ASSIGNER)
				.target(ModelUtil.TARGET)
				.permission(Arrays.asList(Permission.Builder.newInstance()
						.action(Action.USE)
						.constraint(Arrays.asList(Constraint.Builder.newInstance()
								.leftOperand(LeftOperand.COUNT)
								.operator(Operator.EQ)
								.rightOperand("5")
								.build()))
						.build()))
				.build();
		assertNotNull(agreement, "Agreement should be created with all required fields");
	}
	
	@Test
	public void invalidAgreement() {
	assertThrows(ValidationException.class, () ->{
		Agreement.Builder.newInstance()
				.id(ModelUtil.generateUUID())
				.build();
		});
	}
	
}
