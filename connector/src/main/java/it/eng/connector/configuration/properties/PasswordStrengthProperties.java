package it.eng.connector.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "application.password.validator")
@Getter
@Setter
public class PasswordStrengthProperties {

	private int minLength;
	private int maxLength;
	private int minLowerCase;
	private int minUpperCase;
	private int minDigit;
	private int minSpecial;
}
