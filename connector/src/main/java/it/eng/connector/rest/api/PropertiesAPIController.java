package it.eng.connector.rest.api;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.connector.exceptions.PropertyNotFoundAPIException;
import it.eng.connector.exceptions.PropertyNotChangedAPIException;
import it.eng.connector.model.Property;
import it.eng.connector.model.Serializer;
import it.eng.connector.service.PropertiesService.PropertiesService;
import lombok.extern.java.Log;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/connector_property")
@Log
public class PropertiesAPIController {

	private final ApplicationEventPublisher applicationEventPublisher;

	private final PropertiesService propertiesService;

	public PropertiesAPIController(PropertiesService service, ApplicationEventPublisher applicationEventPublisher) {
		super();
		this.propertiesService = service;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Autowired
	private Environment environment;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@GetMapping(path = "/InsertPropertiesFromFiles")
	public ResponseEntity<String> insertPropertiesFromFiles(@RequestParam(required = false) boolean overwrite) {
		log.info("InsertPropertiesFromFiles(...)");
		log.info("overwrite=" + overwrite);

		PropertySource propertySource = ((AbstractEnvironment) environment)
				.getPropertySources().stream()
				.filter(i -> i instanceof OriginTrackedMapPropertySource)
				.filter(i -> i.getName().contains("application.properties"))
				.findFirst()
				.orElseThrow();
		Map<String, String> properties = (Map<String, String>) propertySource.getSource();

        Set<Entry<String, String>> entrySet = properties.entrySet();
        
        for (Iterator<Entry<String, String>> iterator = entrySet.iterator(); iterator.hasNext();) {
			Entry<String, String> entry = (Entry<String, String>) iterator.next();
			//System.err.println(entry);
			
			String key = entry.getKey(); 
			Object value = entry.getValue();
			
			if (propertiesService.getPropertyByName(key).isEmpty() || overwrite) {
				propertiesService.addProperty(
						Property.Builder.newInstance()
						.id(key)
						.key(key)
						.value(value.toString())
						.build());
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append("InsertPropertiesFromFiles complited");

		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(Serializer.serializePlain(sb.toString()));
	}

	@GetMapping(path = "/")
	public ResponseEntity<List<JsonNode>> getProperties() {
		log.info("getProperties()");
		var properties = propertiesService.getProperties();

		applicationEventPublisher.publishEvent(properties);
		
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
				.body(Serializer.serializeProtocolListOfJsonNode(properties));
	}

	@GetMapping(path = "/{key}")
	public ResponseEntity<JsonNode> getPropertyByName(@PathVariable String key) {

		log.info("Fetching property with key '" + key + "'");
		Property property = propertiesService.getPropertyByName(key).orElseThrow(() -> new PropertyNotFoundAPIException("Property with key " + key + " not found"));

		applicationEventPublisher.publishEvent(property);
		return ResponseEntity.ok().header("id", "bar").contentType(MediaType.APPLICATION_JSON)
				.body(Serializer.serializeProtocolJsonNode(property));
	}

	@PostMapping(path = "/")
	public ResponseEntity<JsonNode> addOrModifyProperty(@RequestBody Property property) {
		log.info("addOrModifyProperty(...) ");
		log.info("property = " + property);
		
		Optional<Property> oldOneOpt = propertiesService.getPropertyByName(property.getKey());
		if (oldOneOpt.isEmpty()) {
			propertiesService.addProperty(property);
		} else {
			Property oldOne = oldOneOpt.get();
			if(!property.equals(oldOne)) {
				log.info("Property changed!");
				propertiesService.updateProperty(property, oldOne);
			} else {
				throw new PropertyNotChangedAPIException("Property not updated becouse it has not changed.");
			}
		}
		
		return getPropertyByName(property.getKey());
	}

	@DeleteMapping(path = "/{key}")
	public ResponseEntity<String> deleteProperty(@PathVariable String key) {
		log.info("deleteProperty(...) ");
		log.info("name = " + key);

		propertiesService.getPropertyByName(key).orElseThrow(() -> new PropertyNotFoundAPIException("Property with name" + key + " not Found"));

		propertiesService.deleteProperty(key);
		return ResponseEntity.ok().body("Property deleted successfully");
	}

}
