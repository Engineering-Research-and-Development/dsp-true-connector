package it.eng.connector.service;

import java.util.Arrays;

import org.passay.CharacterRule;
import org.passay.EnglishCharacterData;
import org.passay.LengthRule;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.RuleResult;
import org.passay.WhitespaceRule;
import org.springframework.stereotype.Component;

import it.eng.connector.model.PasswordValidationResult;

@Component
public class PasswordCheckValidator {

	public PasswordValidationResult isValid(String password) {
		// TODO make this config from Mongo or property files
		final PasswordValidator validator = new PasswordValidator(Arrays.asList(
				new LengthRule(8, 30), 
				new CharacterRule(EnglishCharacterData.UpperCase, 1),
				new CharacterRule(EnglishCharacterData.LowerCase, 1), 
				new CharacterRule(EnglishCharacterData.Digit, 1),
				new CharacterRule(EnglishCharacterData.Special, 1), 
				new WhitespaceRule()));
		final RuleResult result = validator.validate(new PasswordData(password));
		PasswordValidationResult validationResult = new PasswordValidationResult();
		if (result.isValid()) {
			return validationResult.valid(password);
		}
		return validationResult.invalid(password, validator.getMessages(result));
	}

}
