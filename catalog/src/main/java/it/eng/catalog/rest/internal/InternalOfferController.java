package it.eng.catalog.rest.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
import it.eng.catalog.model.Offer;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.CatalogService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint that validates an offer for the connector's own loopback calls.
 *
 * <p>Served under {@code /internal/**}, which accepts only an internal-call token from this process sent
 * from loopback, and is not part of the published OpenAPI documentation.
 */
@Hidden
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE,
        path = ApiEndpoints.INTERNAL_OFFERS_V1)
@Slf4j
public class InternalOfferController {
	
	private final CatalogService catalogService;

    /**
     * Creates the controller.
     *
     * @param service the catalog service that validates offers
     */
    public InternalOfferController(CatalogService service) {
        super();
        this.catalogService = service;
    }
	
    /**
     * Validates an offer against the catalog of the current tenant.
     *
     * @param offerJson the offer, as plain JSON
     * @return 200 when the offer is valid, 400 otherwise
     */
    @PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericApiResponse<String>> validateOffer(@RequestBody JsonNode offerJson) {
        log.info("Validating offer");
        
        Offer offer = CatalogSerializer.deserializePlain(offerJson, Offer.class);

        boolean isValid = catalogService.validateOffer(offer);
        
        if (isValid) {
        	return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        			.body(GenericApiResponse.success("Offer is valid", "Offer is valid"));
		}
        
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
        		.body(GenericApiResponse.error("Offer not valid"));
    }

}
