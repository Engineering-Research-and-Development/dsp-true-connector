package it.eng.catalog.rest.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.eng.catalog.model.Offer;
import it.eng.catalog.model.OfferResponse;
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.CatalogService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, 
	path = ApiEndpoints.CATALOG_OFFERS_V1)
@Slf4j
public class OfferAPIController {
	
	private final CatalogService catalogService;

    public OfferAPIController(CatalogService service) {
        super();
        this.catalogService = service;
    }
	
    @PostMapping(path = "/validate")
    public ResponseEntity<GenericApiResponse<OfferResponse>> validateOffer(@RequestBody String offerString) {
        log.info("Validating offer");
        
        Offer offer = Serializer.deserializePlain(offerString, Offer.class);

        OfferResponse offerResponse = catalogService.validateOffer(offer);
        
        if (offerResponse.isValid()) {
        	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        			.body(GenericApiResponse.success(offerResponse, "Offer is valid"));
		}
        
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
        		.body(GenericApiResponse.error("Offer not valid"));
    }

}
