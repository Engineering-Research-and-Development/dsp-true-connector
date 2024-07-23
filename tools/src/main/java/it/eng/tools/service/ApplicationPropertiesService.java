package it.eng.tools.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
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
import lombok.extern.java.Log;

/**
 * The PropertiesService class provides methods to interact with properties, including saving, retrieving, and deleting properties.
 */
@Service
@Log
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
			log.warning(key + " not found in the db, try in application.properties");
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

//	public Optional<ApplicationProperty> getStoredPropertyByKey(String key) {
//		return repository.findById(key);
//	}

	/*
	 * public void deleteProperty(String name) { repository.deleteById(name); }
	 */

	/**
	 * Private method for creating base builder for application property update by its ID.
	 *
	 * @param id The ID of the application property for update.
	 * @return The builder for the application property  with basic mandatory unchanged fields.
	 * @throws ApplicationPropertyErrorException Thrown if the application property  with the specified ID is not found.
	 */
	/*
	 * public void updateProperty(String key, String value) {
	 *
	 * System.out.println("\n\n\n" + env);
	 *
	 * ConfigurableEnvironment configurableEnvironment = (ConfigurableEnvironment)
	 * env; MutablePropertySources propertySources =
	 * configurableEnvironment.getPropertySources();
	 *
	 * PropertySource<?> ap = propertySources.get("applicationProperties"); if(ap !=
	 * null) { System.out.println(ap.getSource().getClass().getName());
	 *
	 * Map aaa = (Map)ap.getSource();
	 *
	 * System.out.println(aaa.entrySet()); }
	 *
	 * Map map = new HashMap<String,String>(); map.put(key, value);
	 *
	 * propertySources.addFirst(new MapPropertySource("applicationProperties",
	 * map));
	 *
	 * MutablePropertySources ps = ((AbstractEnvironment) env).getPropertySources();
	 * Iterator<PropertySource<?>> ips = ps.iterator();
	 *
	 * while(ips.hasNext()) { PropertySource<?> currentps = ips.next();
	 *
	 * String name = currentps.getName(); Object source = currentps.getSource();
	 * System.out.println("\n\n\nname=" + name +
	 * (currentps.getProperty("spring.ssl.bundle.jks.connector.keystore.location")
	 * != null ?" YES":" NO")); System.out.println("\n" +
	 * currentps.getClass().getName() + "\t" + currentps.toString());
	 * System.out.println("\n" + source.getClass().getName() + "\t" +
	 * source.toString() + "\n\n\n"); }
	 *
	 * }
	 */

	/*
	 * public void saveAllPropertiesOnEnv() { ConfigurableEnvironment
	 * configurableEnvironment = (ConfigurableEnvironment) env;
	 * MutablePropertySources propertySources =
	 * configurableEnvironment.getPropertySources(); PropertySource<?>
	 * customPropertySource = propertySources.get("customPropertySource"); if
	 * (customPropertySource != null) { // Write properties to configuration file //
	 * For example, write to application.properties
	 *
	 * //TODO: manage add on env when customPropertySource exists } }
	 */

	/*public String addPropertyToApplicationPropertySource(String key, Object value) {
		log.info("addPropertyToApplicationPropertySource("+key+", "+value+")");

		ConfigurableEnvironment configurableEnvironment = (ConfigurableEnvironment) env;
		MutablePropertySources propertySources = configurableEnvironment.getPropertySources();

		Map<String,Object> storedApplicationPropertiesMap = new HashMap<String,Object>();

		PropertySource<?> storedApplicationPropertiesSource = propertySources.get(STORED_APPLICATION_PROPERTIES);
		if(storedApplicationPropertiesSource != null) {
			storedApplicationPropertiesMap = (Map)storedApplicationPropertiesSource.getSource();
		}

		storedApplicationPropertiesMap.put(key, value);

		propertySources.addFirst(new MapPropertySource(STORED_APPLICATION_PROPERTIES, storedApplicationPropertiesMap));

		return env.getProperty(key);
	}*/

	public void addPropertyOnEnv(String key, Object value, Environment environment) {
		///if(environment != null) {
			ConfigurableEnvironment configurableEnvironment = (ConfigurableEnvironment) environment;
			MutablePropertySources propertySources = configurableEnvironment.getPropertySources();

			Map storedApplicationPropertiesMap = new HashMap<String, String>();

			PropertySource<?> storedApplicationPropertiesSource = propertySources.get(STORED_APPLICATION_PROPERTIES);
			if(storedApplicationPropertiesSource != null) {
				storedApplicationPropertiesMap = (Map)storedApplicationPropertiesSource.getSource();
			}

			storedApplicationPropertiesMap.put(key, value);

			propertySources.addFirst(new MapPropertySource(STORED_APPLICATION_PROPERTIES, storedApplicationPropertiesMap));
		///}

			log.info(key + "=" + environment.getProperty(key));
	}

	public String get(String key) {
		return env.getProperty(key);
	}

}
