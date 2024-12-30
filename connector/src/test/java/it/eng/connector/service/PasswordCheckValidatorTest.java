package it.eng.connector.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.eng.connector.configuration.properties.PasswordStrengthProperties;
import it.eng.connector.model.PasswordValidationResult;

@ExtendWith(MockitoExtension.class)
class PasswordCheckValidatorTest {
	
	@Mock
	private PasswordStrengthProperties passwordStrengthProperties;
	
	@InjectMocks
	private PasswordCheckValidator passwordCheckValidator;
	
	@BeforeEach
	public void getPasswordConfiguration() {
		when(passwordStrengthProperties.getMinLength()).thenReturn(8);
		when(passwordStrengthProperties.getMaxLength()).thenReturn(16);
		when(passwordStrengthProperties.getMinUpperCase()).thenReturn(1);
		when(passwordStrengthProperties.getMinLowerCase()).thenReturn(1); 
		when(passwordStrengthProperties.getMinDigit()).thenReturn(1);
		when(passwordStrengthProperties.getMinSpecial()).thenReturn(1);
	}

	@Test
	@DisplayName("Password check - invalid password, not cmpliant with restrictions")
	void testIsValid_false() {
		PasswordValidationResult validationResult  = passwordCheckValidator.isValid("invalid");
		assertFalse(validationResult.isValid());
		assertFalse(validationResult.getViolations().isEmpty());
	}
	
	@Test
	@DisplayName("Password valid - compliant with restrictions")
	void testIsValid() {
		// 8-30 chars, 1 upper, one lower, 1 digit, 1 special char
		PasswordValidationResult validationResult  = passwordCheckValidator.isValid("ValidPassword1!");
		assertTrue(validationResult.isValid());
		assertTrue(validationResult.getViolations().isEmpty());
	}

}
