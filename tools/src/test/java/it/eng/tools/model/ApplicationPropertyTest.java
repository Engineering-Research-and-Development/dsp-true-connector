package it.eng.tools.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ValidationException;

public class ApplicationPropertyTest {

	@Test
	@DisplayName("Verify @NotNull key in Application Property")
	public void testKeyNull() {
		Exception exception = assertThrows(ValidationException.class, () -> {
			ApplicationProperty.Builder.newInstance().key(null).build();
	    });

	    String actualMessage = exception.getMessage();

	    assertFalse(actualMessage.isBlank());
	}

}
