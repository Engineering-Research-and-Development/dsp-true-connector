package it.eng.catalog.rest.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.Offer;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.CatalogService;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = "/api/offer")
@Slf4j
public class OfferAPIController {
	
	private final CatalogService catalogService;

    public OfferAPIController(CatalogService service) {
        super();
        this.catalogService = service;
    }
	
    @PostMapping(path = "/validateOffer")
    public ResponseEntity<JsonNode> validateOffer(@RequestBody String offerString) {
        log.info("Validating offer");
        
        Offer offer = Serializer.deserializePlain(offerString, Offer.class);

        boolean isValid = catalogService.validateOffer(offer);
        
        if (isValid) {
        	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).build();
		}
        
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).build();
        

    }

}
