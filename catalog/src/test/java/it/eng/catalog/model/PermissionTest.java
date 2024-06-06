package it.eng.catalog.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

public class PermissionTest {

	private static final String TARGET_A = "urn:uuid:TARGET";
	
	private Constraint constraint = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.DATE_TIME)
			.operator(Operator.GT)
			.rightOperand("2024-02-29T00:00:01+01:00")
			.build();
	
	private Permission permissionA = Permission.Builder.newInstance()
			.action(Action.USE)
			.target(TARGET_A)
			.constraint(Set.of(constraint))
			.build();
	
	private Permission permissionB = Permission.Builder.newInstance()
			.action(Action.ANONYMIZE)
			.target(TARGET_A)
			.constraint(Set.of(constraint))
			.build();
	
	@Test
	public void equalsTrue() {
		assertTrue(permissionA.equals(permissionA));
	}

	@Test
	public void equalsFalse() {
		assertFalse(permissionA.equals(permissionB));
	}
	
	@Test
	public void equalsFalse_array() {
		 Constraint constraint2 = Constraint.Builder.newInstance()
					.leftOperand(LeftOperand.DATE_TIME)
					.operator(Operator.EQ)
					.rightOperand("2024-02-29T00:00:01+01:00")
					.build();
		Permission permissionArray = Permission.Builder.newInstance()
				.action(Action.ANONYMIZE)
				.target(TARGET_A)
				.constraint(Set.of(constraint2, constraint))
				.build();
		assertFalse(permissionA.equals(permissionArray));
	}
	
	@Test
	public void equalsTrue_array() {
		Constraint constraint1 = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.DATE_TIME)
				.operator(Operator.GT)
				.rightOperand("2024-02-29T00:00:01+01:00")
				.build();
		 Constraint constraint2 = Constraint.Builder.newInstance()
					.leftOperand(LeftOperand.COUNT)
					.operator(Operator.EQ)
					.rightOperand("5")
					.build();
		 
		Permission permissionA = Permission.Builder.newInstance()
					.action(Action.USE)
					.target(TARGET_A)
					.constraint(Set.of(constraint1, constraint2))
					.build();
		Permission permissionB = Permission.Builder.newInstance()
				.action(Action.USE)
				.target(TARGET_A)
				.constraint(Set.of(constraint2, constraint1))
				.build();

		assertTrue(permissionA.equals(permissionB));
	}
	
}
