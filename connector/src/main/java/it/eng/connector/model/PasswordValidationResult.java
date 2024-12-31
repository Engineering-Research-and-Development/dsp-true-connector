package it.eng.connector.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PasswordValidationResult {

	private String password;
	private boolean isValid;
	private List<String> violations;
	
	public PasswordValidationResult valid(String passwod) {
		return new PasswordValidationResult(password, true, List.of());
	}
	
	public PasswordValidationResult invalid(String password, List<String> violations) {
		return new PasswordValidationResult(password, false, violations);
	}
}
