package it.eng.catalog.rest.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import it.eng.catalog.model.Offer;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.util.CatalogMockObjectUtil;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
public class OfferAPIControllerTest {
	
	    @InjectMocks
	    private OfferAPIController offerAPIController;

	    @Mock
	    private CatalogService catalogService;
	    
	    @Test
	    @DisplayName("Validate offer - offer is valid")
	    public void offerValidationSuccessfulTest() {
	        Offer offer = CatalogMockObjectUtil.OFFER;
	    	when(catalogService.validateOffer(any(Offer.class))).thenReturn(true);
	        
	        ResponseEntity<GenericApiResponse<String>> response = offerAPIController.validateOffer(CatalogSerializer.serializePlain(offer));

	        verify(catalogService).validateOffer(any(Offer.class));
	        assertNotNull(response);
	        assertTrue(response.getStatusCode().is2xxSuccessful());
	        assertTrue(StringUtils.contains(response.getBody().getMessage(), "Offer is valid"));
	    }
	    
	    @Test
	    @DisplayName("Validate offer - offer not valid")
	    public void offerValidationFailedTest() {
	    	Offer offer = CatalogMockObjectUtil.OFFER;
	    	when(catalogService.validateOffer(any(Offer.class))).thenReturn(false);
		    
	    	ResponseEntity<GenericApiResponse<String>> response = offerAPIController.validateOffer(CatalogSerializer.serializePlain(offer));

		    verify(catalogService).validateOffer(any(Offer.class));
	        assertNotNull(response);
	        assertTrue(response.getStatusCode().is4xxClientError());
	        assertTrue(StringUtils.contains(response.getBody().getMessage(), "Offer not valid"));
	    }
}
