package it.eng.connector.service.PropertiesService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import it.eng.connector.exceptions.PropertyErrorException;
import it.eng.connector.model.Property;
import it.eng.connector.repository.PropertiesRepository;

/**
 * The PropertiesService class provides methods to interact with properties, including saving, retrieving, and deleting properties.
 */
@Service
public class PropertiesService {

	private final PropertiesRepository repository;

	public PropertiesService(PropertiesRepository repository) {
		this.repository = repository;
	}

	public List<Property> getProperties() {
		List<Property> allProperties = repository.findAll(sortByIdAsc());

		if (allProperties.isEmpty()) {
			throw new PropertyErrorException("Property not found");
		} else {
			return allProperties;
		}
	}

	private Sort sortByIdAsc() {
		return Sort.by("id");
	}

	public Optional<Property> getPropertyByName(String name) {
		return repository.findById(name);
	}

	public void addProperty(Property property) {
		repository.insert(property);
	}

	public void deleteProperty(String name) {
		repository.deleteById(name);		
	}

	public void updateProperty(Property property, Property oldOne) {
		property.setId(oldOne.getId());
		property.setInsertDate(oldOne.getInsertDate());
		
		repository.save(property);
	}

}
