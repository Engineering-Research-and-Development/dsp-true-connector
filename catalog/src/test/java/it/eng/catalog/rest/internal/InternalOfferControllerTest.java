package it.eng.catalog.rest.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
import it.eng.catalog.model.Offer;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import org.apache.commons.lang3.Strings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class InternalOfferControllerTest {
	
	    @InjectMocks
	    private InternalOfferController offerAPIController;

	    @Mock
	    private CatalogService catalogService;
	    
	    @Test
	    @DisplayName("The controller is mapped under /internal/v1/offers and hidden from the OpenAPI docs")
	    public void mappedUnderInternalZoneAndHidden() {
	        assertArrayEquals(new String[]{ApiEndpoints.INTERNAL_OFFERS_V1},
	                InternalOfferController.class.getAnnotation(RequestMapping.class).path());
	        assertNotNull(InternalOfferController.class.getAnnotation(Hidden.class));
	    }

	    @Test
	    @DisplayName("Validate offer - offer is valid")
	    public void offerValidationSuccessfulTest() {
	        Offer offer = CatalogMockObjectUtil.OFFER;
	    	when(catalogService.validateOffer(any(Offer.class))).thenReturn(true);
	        
            JsonNode offerJson = CatalogSerializer.serializePlainJsonNode(offer);
            ResponseEntity<GenericApiResponse<String>> response = offerAPIController.validateOffer(offerJson);

	        verify(catalogService).validateOffer(any(Offer.class));
	        assertNotNull(response);
	        assertTrue(response.getStatusCode().is2xxSuccessful());
	        assertTrue(Strings.CS.contains(response.getBody().getMessage(), "Offer is valid"));
	    }
	    
	    @Test
	    @DisplayName("Validate offer - offer not valid")
	    public void offerValidationFailedTest() {
	    	Offer offer = CatalogMockObjectUtil.OFFER;
	    	when(catalogService.validateOffer(any(Offer.class))).thenReturn(false);
		    
            JsonNode offerJson = CatalogSerializer.serializePlainJsonNode(offer);
            ResponseEntity<GenericApiResponse<String>> response = offerAPIController.validateOffer(offerJson);

		    verify(catalogService).validateOffer(any(Offer.class));
	        assertNotNull(response);
	        assertTrue(response.getStatusCode().is4xxClientError());
	        assertTrue(Strings.CS.contains(response.getBody().getMessage(), "Offer not valid"));
	    }
}
