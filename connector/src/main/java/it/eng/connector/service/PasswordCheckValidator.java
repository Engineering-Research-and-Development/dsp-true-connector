package it.eng.connector.service;

import java.util.Arrays;

import org.passay.CharacterRule;
import org.passay.EnglishCharacterData;
import org.passay.LengthRule;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.RuleResult;
import org.springframework.stereotype.Component;

import it.eng.connector.configuration.properties.PasswordStrengthProperties;
import it.eng.connector.model.PasswordValidationResult;

@Component
public class PasswordCheckValidator {
	
	private final PasswordStrengthProperties passwordStrengthProperties;
	
	public PasswordCheckValidator(PasswordStrengthProperties passwordStrengthProperties) {
		super();
		this.passwordStrengthProperties = passwordStrengthProperties;
	}

	public PasswordValidationResult isValid(String password) {
		final PasswordValidator validator = new PasswordValidator(Arrays.asList(
				new LengthRule(passwordStrengthProperties.getMinLength(), passwordStrengthProperties.getMaxLength()), 
				new CharacterRule(EnglishCharacterData.UpperCase, passwordStrengthProperties.getMinUpperCase()),
				new CharacterRule(EnglishCharacterData.LowerCase, passwordStrengthProperties.getMinLowerCase()), 
				new CharacterRule(EnglishCharacterData.Digit, passwordStrengthProperties.getMinDigit()),
				new CharacterRule(EnglishCharacterData.Special, passwordStrengthProperties.getMinSpecial())));
		final RuleResult result = validator.validate(new PasswordData(password));
		PasswordValidationResult validationResult = new PasswordValidationResult();
		if (result.isValid()) {
			return validationResult.valid(password);
		}
		return validationResult.invalid(password, validator.getMessages(result));
	}

}
