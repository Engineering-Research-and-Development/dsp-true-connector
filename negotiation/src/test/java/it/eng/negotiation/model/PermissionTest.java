package it.eng.negotiation.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
