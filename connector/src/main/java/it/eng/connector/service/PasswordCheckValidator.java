package it.eng.connector.service;

import java.util.Arrays;

import org.passay.CharacterRule;
import org.passay.EnglishCharacterData;
import org.passay.LengthRule;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.RuleResult;
import org.springframework.stereotype.Component;

import it.eng.connector.configuration.PasswordValidationConfiguration;
import it.eng.connector.model.PasswordValidationResult;

@Component
public class PasswordCheckValidator {
	
	private final PasswordValidationConfiguration passwordValidationConfiguration;
	
	public PasswordCheckValidator(PasswordValidationConfiguration passwordValidationConfiguration) {
		super();
		this.passwordValidationConfiguration = passwordValidationConfiguration;
	}

	public PasswordValidationResult isValid(String password) {
		final PasswordValidator validator = new PasswordValidator(Arrays.asList(
				new LengthRule(passwordValidationConfiguration.getMinLength(), passwordValidationConfiguration.getMaxLength()), 
				new CharacterRule(EnglishCharacterData.UpperCase, passwordValidationConfiguration.getMinUpperCase()),
				new CharacterRule(EnglishCharacterData.LowerCase, passwordValidationConfiguration.getMinLowerCase()), 
				new CharacterRule(EnglishCharacterData.Digit, passwordValidationConfiguration.getMinDigit()),
				new CharacterRule(EnglishCharacterData.Special, passwordValidationConfiguration.getMinSpecial())));
		final RuleResult result = validator.validate(new PasswordData(password));
		PasswordValidationResult validationResult = new PasswordValidationResult();
		if (result.isValid()) {
			return validationResult.valid(password);
		}
		return validationResult.invalid(password, validator.getMessages(result));
	}

}
