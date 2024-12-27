package it.eng.connector.configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.service.ApplicationPropertiesService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PasswordValidationConfiguration {

	private final ApplicationPropertiesService applicationPropertiesService;
	private final Map<String, String> validationConfig;
	
	private final static String MIN_LENGTH = "application.password.validator.minLength";
	private final static String MAX_LENGTH = "application.password.validator.maxLength";
	private final static String MIN_LOWER_CASE = "application.password.validator.minLowerCase";
	private final static String MIN_UPPER_CASE = "application.password.validator.minUpperCase";
	private final static String MIN_DIGIT = "application.password.validator.minDigit";
	private final static String MIN_SPECIAL = "application.password.validator.minSpecial";

	public PasswordValidationConfiguration(ApplicationPropertiesService applicationPropertiesService) {
		super();
		this.applicationPropertiesService = applicationPropertiesService;
		this.validationConfig = new HashMap<>();
	}
	
	@EventListener
	void handleApplicationPropertyChange(ApplicationProperty property) {
		if(property.getKey().startsWith("application.password.validator")) {
			log.info("Handling event - application property update for key {}");
			applicationPropertiesService.getPropertyByKey(property.getKey())
			.ifPresent(p -> {
				validationConfig.put(p.getKey(), p.getValue());
			});
		}
	}
	
	public int getMaxLength() {
		return Integer.valueOf(getPropertyForKey(MAX_LENGTH));
	}
	
	public int getMinLength() {
		return Integer.valueOf(getPropertyForKey(MIN_LENGTH));
	}
	
	public int getMinLowerCase() {
		return Integer.valueOf(getPropertyForKey(MIN_LOWER_CASE));
	}
	
	public int getMinUpperCase() {
		return Integer.valueOf(getPropertyForKey(MIN_UPPER_CASE));
	}
	
	public int getMinDigit() {
		return Integer.valueOf(getPropertyForKey(MIN_DIGIT));
	}
	
	public int getMinSpecial() {
		return Integer.valueOf(getPropertyForKey(MIN_SPECIAL));
	}
	
	private String getPropertyForKey(String key) {
		if(validationConfig.isEmpty()) {
			List<ApplicationProperty> properties = applicationPropertiesService.getProperties("application.password.validator");
			properties.forEach(prop -> validationConfig.put(prop.getKey(), prop.getValue()));
		}
		return validationConfig.get(key);
	}
}
