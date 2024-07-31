package it.eng.tools.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import it.eng.tools.exception.ApplicationPropertyErrorException;
import it.eng.tools.exception.ApplicationPropertyNotFoundAPIException;
import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.repository.ApplicationPropertiesRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The PropertiesService class provides methods to interact with properties, including saving, retrieving, and deleting properties.
 */
@Service
@Slf4j
public class ApplicationPropertiesService {

	private static final String STORED_APPLICATION_PROPERTIES = "storedApplicationProperties";

	private Environment env;

	private final ApplicationPropertiesRepository repository;

	private Sort sortByIdAsc() {
		return Sort.by("id");
	}

	public ApplicationPropertiesService(ApplicationPropertiesRepository repository, Environment env) {
		this.repository = repository;
		this.env = env;
	}

	public List<ApplicationProperty> getProperties(String key_prefix) {

		List<ApplicationProperty> allProperties = null;

		if(!StringUtils.isBlank(key_prefix)) {
			allProperties = repository.findByKeyStartingWith(key_prefix, sortByIdAsc());
		} else {
			allProperties = repository.findAll(sortByIdAsc());
		}

		if (allProperties.isEmpty()) {
			throw new ApplicationPropertyErrorException("Property not found");
		} else {
			return allProperties;
		}
	}

	public Optional<ApplicationProperty> getPropertyByKey(String key) {
		Optional<ApplicationProperty> propertyByMongo = repository.findById(key);
		if(propertyByMongo.isEmpty()) {
			log.warn(key + " not found in the db, try in application.properties");
			//Try to keep value from applicatio.properties
			String propertyValueByApplicationProperty = env.getProperty(key);

			if(propertyValueByApplicationProperty != null) {
				log.info(key + " value found in application.properties. Add in db.");
				ApplicationProperty storedProperty = addPropertyOnMongo(ApplicationProperty.Builder
						.newInstance()
						.key(key)
						.value(propertyValueByApplicationProperty)
						.build());

				addPropertyOnMongo(storedProperty);

				return Optional.ofNullable(storedProperty);
			}
		}
		return propertyByMongo;
	}

	private ApplicationProperty addPropertyOnMongo(ApplicationProperty property) {
		return repository.save(property);
	}

	public ApplicationProperty updateProperty(ApplicationProperty property, ApplicationProperty oldOne) {

		ApplicationProperty.Builder builder = returnBaseApplicationPropertyForUpdate(oldOne.getKey());

		builder
		.value(property.getValue());

		ApplicationProperty updatedApplicationProperty = builder.build();
		//ApplicationProperty storedApplicationProperty = repository.save(updatedApplicationProperty);

		return addPropertyOnMongo(updatedApplicationProperty);
	}

	private ApplicationProperty.Builder returnBaseApplicationPropertyForUpdate(String key) {
		return repository.findById(key)
				.map(c -> ApplicationProperty.Builder.newInstance()
						.key(key)
						.version((c.getVersion() != null ? c.getVersion() : 0))
						.issued(c.getIssued())
						.createdBy(c.getCreatedBy())
						//.modified(Instant.now())
						)
				.orElseThrow(() -> new ApplicationPropertyNotFoundAPIException("ApplicationProperty with key: " + key + " not found"));
	}


	/* This method is used to overwrite property values ​​in the env */
	public void addPropertyOnEnv(String key, Object value, Environment environment) {
		ConfigurableEnvironment configurableEnvironment = (ConfigurableEnvironment) environment;
		MutablePropertySources propertySources = configurableEnvironment.getPropertySources();

		Map storedApplicationPropertiesMap = new HashMap<String, String>();

		PropertySource<?> storedApplicationPropertiesSource = propertySources.get(STORED_APPLICATION_PROPERTIES);
		if(storedApplicationPropertiesSource != null) {
			storedApplicationPropertiesMap = (Map)storedApplicationPropertiesSource.getSource();
		}

		storedApplicationPropertiesMap.put(key, value);

		propertySources.addFirst(new MapPropertySource(STORED_APPLICATION_PROPERTIES, storedApplicationPropertiesMap));

		log.info(key + "=" + environment.getProperty(key));
	}

	public String get(String key) {
		String value = env.getProperty(key);
		if(value == null) {
			Optional<ApplicationProperty> propertyByMongo = repository.findById(key);
			if(!propertyByMongo.isEmpty()) {
				value = propertyByMongo.get().getValue();
			}
		}
		return value;
	}

	public void copyApplicationPropertiesToEnvironment(Environment environment) {
		try {
			List<ApplicationProperty> allApplicationPropertiesOnMongo = getProperties(null);

			for (Iterator<ApplicationProperty> iterator = allApplicationPropertiesOnMongo.iterator(); iterator.hasNext();) {
				ApplicationProperty applicationProperty = (ApplicationProperty) iterator.next();
				addPropertyOnEnv(applicationProperty.getKey(), applicationProperty.getValue(), environment);
			}
		} catch (ApplicationPropertyErrorException e) {
			log.warn("Any property found in MongoDB!");
		}
	}

}
