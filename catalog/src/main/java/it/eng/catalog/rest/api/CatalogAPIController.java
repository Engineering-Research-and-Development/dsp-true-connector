package it.eng.catalog.rest.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import it.eng.catalog.entity.CatalogEntity;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Serializer;
import it.eng.catalog.service.CatalogService;
import it.eng.tools.exception.ResourceNotFoundException;
import lombok.extern.java.Log;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/catalog")
@Log
public class CatalogAPIController {
	
	@Autowired
    private ApplicationEventPublisher applicationEventPublisher;

	private final CatalogService service;
	
	public CatalogAPIController(CatalogService service) {
		super();
		this.service = service;
	}

//	@GetMapping(path = "/")
//	public ResponseEntity<List<Catalog>> getAllCatalogs() {
//		log.info("Fetching all catalogs");
//		return ResponseEntity.ok().header("id", "bar").contentType(MediaType.APPLICATION_JSON)
//				.body(service.findAll());
//	}
	
	@GetMapping(path = "/{id}")
	public ResponseEntity<String> getCatalogEntityById(@PathVariable String id) {
		try {
			log.info("Fetching catalog with id '" + id + "'");
			Catalog c = service.findById(id);
			applicationEventPublisher.publishEvent(c);
			return ResponseEntity.ok().header("id", "bar").contentType(MediaType.APPLICATION_JSON)
					.body(Serializer.serializePlain(c));
		} catch (ResourceNotFoundException exc) {
			log.info("Catalog with id '" + id + "' not found");
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog Not Found", exc);
		}
	}
	
	@PostMapping
	public ResponseEntity<CatalogEntity> createCatalog(@RequestBody Catalog catalog) {
		service.save(catalog);
		return ResponseEntity.ok().body(new CatalogEntity());
	}

}
