package it.eng.tools.configuration;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.repository.ApplicationPropertiesRepository;
import it.eng.tools.service.ApplicationPropertiesService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ApplicationPropertiesConfiguration {

	//@Autowired
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

		PropertySource propertySource = ((AbstractEnvironment) environment)
				.getPropertySources().stream()
				.filter(i -> i instanceof OriginTrackedMapPropertySource)
				.filter(i -> i.getName().contains("application.properties"))
				.findFirst()
				.orElseThrow();
		Map<String, String> properties = (Map<String, String>) propertySource.getSource();

		log.info("Size:" + properties.size());

		//List<ApplicationProperty> propertiesToStore = new ArrayList<ApplicationProperty>(properties.size());

		Set<Entry<String, String>> entrySet = properties.entrySet();
		entrySet.forEach(entry -> {
			String key = entry.getKey();
			Object value = entry.getValue();
			log.info(entry.getKey() + "=" + value);

			ApplicationProperty applicationProperty = managePropertyOnEnv(key, value);

			log.info(applicationProperty.toString());

		});
	}

	private ApplicationProperty managePropertyOnEnv(String key, Object value) {
		Optional<ApplicationProperty> propertyByMongo = repository.findById(key);
		ApplicationProperty ap = null;

		if(propertyByMongo.isEmpty()) {
			ap = ApplicationProperty.Builder.newInstance()/*.id(key)*/.key(key).value(value.toString()).build();

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

}