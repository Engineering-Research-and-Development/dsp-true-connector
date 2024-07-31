package it.eng.tools.configuration;

//import java.util.Map;
//import java.util.Map.Entry;
//import java.util.Optional;
//import java.util.Set;

//import org.springframework.boot.env.OriginTrackedMapPropertySource;
//import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
//import org.springframework.core.env.PropertySource;
//import org.springframework.stereotype.Component;
import org.springframework.stereotype.Component;

//import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.repository.ApplicationPropertiesRepository;
import it.eng.tools.service.ApplicationPropertiesService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ApplicationPropertiesConfiguration {

	private Environment environment;

	private final ApplicationPropertiesService service;

	private final ApplicationPropertiesRepository repository;


	public ApplicationPropertiesConfiguration(Environment environment, ApplicationPropertiesService service, ApplicationPropertiesRepository repository) {
		super();
		this.environment = environment;
		this.repository = repository;
		this.service = service;
	}

	@PostConstruct
	public void init() {
		log.info("init() is running");
		//ApplicationPropertiesService service = new ApplicationPropertiesService(repository);

		/* add ApplicationProperties on MongoDB to env */
		service.copyApplicationPropertiesToEnvironment(environment);

		/* Propagate application.properties values on MongoDB
		 * At this moment we preferred to disable this part which searches for
		 * the properties inserted via the application.properties file and
		 * inserts them into mongo. Any value on MongoDB has a higher priority.

		 PropertySource propertySource = ((AbstractEnvironment) environment)
				.getPropertySources().stream()
				.filter(i -> i instanceof OriginTrackedMapPropertySource)
				.filter(i -> i.getName().contains("application.properties"))
				.findFirst()
				.orElseThrow();
		Map<String, String> properties = (Map<String, String>) propertySource.getSource();

		log.info("Size:" + properties.size());

		Set<Entry<String, String>> entrySet = properties.entrySet();
		entrySet.forEach(entry -> {
			String key = entry.getKey();
			Object value = entry.getValue();
			log.info(entry.getKey() + "=" + value);

			ApplicationProperty applicationProperty = managePropertyOnEnv(key, value);

			log.info(applicationProperty.toString());

		});

		*/
	}

	/* This method was commented as it was called from the commented part of
	 * the init() method

	private ApplicationProperty managePropertyOnEnv(String key, Object value) {
		Optional<ApplicationProperty> propertyByMongo = repository.findById(key);
		ApplicationProperty ap = null;

		if(propertyByMongo.isEmpty()) {
			ap = ApplicationProperty.Builder.newInstance().key(key).value(value.toString()).build();

			ap = repository.insert(ap);
		} else {
			ap = propertyByMongo.get();

			if(!ap.getValue().equals(value.toString())) {
				log.info("Different value between mongo and env. Upgrade value on env.");

				service.addPropertyOnEnv(key, ap.getValue(), environment);
			}
		}

		return ap;
	}
	*/

}