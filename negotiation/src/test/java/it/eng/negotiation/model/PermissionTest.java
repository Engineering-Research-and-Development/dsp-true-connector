package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import jakarta.validation.ValidationException;

public class PermissionTest {

	@Test
	public void validPermission() {
		Permission permission = Permission.Builder.newInstance()
				.action(Action.USE)
				.constraint(Arrays.asList(ModelUtil.CONSTRAINT))
				.build();
		assertNotNull(permission, "Permission creted with mandatory fields");
	}
	
	@Test
	public void invalidPermission() {
		assertThrows(ValidationException.class, 
				() -> Permission.Builder.newInstance()
					.assignee(ModelUtil.ASSIGNEE)
					.assigner(ModelUtil.ASSIGNER)
					.build());
	}
	
	@Test
	public void equalsTrue() {
		Permission permission = Permission.Builder.newInstance()
				.action(Action.USE)
				.constraint(Arrays.asList(ModelUtil.CONSTRAINT))
				.build();
		Permission permissionB = Permission.Builder.newInstance()
				.action(Action.USE)
				.constraint(Arrays.asList(ModelUtil.CONSTRAINT))
				.build();
		assertTrue(permission.equals(permissionB));
	}

	@Test
	public void equalsFalse() {
		Permission permissionA = Permission.Builder.newInstance()
				.action(Action.USE)
				.constraint(Arrays.asList(ModelUtil.CONSTRAINT))
				.build();
		Permission permissionB = Permission.Builder.newInstance()
				.action(Action.ANONYMIZE)
				.constraint(Arrays.asList(ModelUtil.CONSTRAINT))
				.build();
		assertFalse(permissionA.equals(permissionB));
	}
}

