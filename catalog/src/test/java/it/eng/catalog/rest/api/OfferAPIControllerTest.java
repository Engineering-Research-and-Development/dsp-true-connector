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
import it.eng.catalog.serializer.Serializer;
import it.eng.catalog.service.CatalogService;
import it.eng.catalog.util.MockObjectUtil;
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
	        Offer offer = MockObjectUtil.OFFER;
	    	when(catalogService.validateOffer(any(Offer.class))).thenReturn(true);
	        
	        ResponseEntity<GenericApiResponse<String>> response = offerAPIController.validateOffer(Serializer.serializePlain(offer));

	        verify(catalogService).validateOffer(any(Offer.class));
	        assertNotNull(response);
	        assertTrue(response.getStatusCode().is2xxSuccessful());
	        assertTrue(StringUtils.contains(response.getBody().getMessage(), "Offer is valid"));
	    }
	    
	    @Test
	    @DisplayName("Validate offer - offer not valid")
	    public void offerValidationFailedTest() {
	    	Offer offer = MockObjectUtil.OFFER;
	    	when(catalogService.validateOffer(any(Offer.class))).thenReturn(false);
		    
	    	ResponseEntity<GenericApiResponse<String>> response = offerAPIController.validateOffer(Serializer.serializePlain(offer));

		    verify(catalogService).validateOffer(any(Offer.class));
	        assertNotNull(response);
	        assertTrue(response.getStatusCode().is4xxClientError());
	        assertTrue(StringUtils.contains(response.getBody().getMessage(), "Offer not valid"));
	    }
	    
	    @Test
	    public void aaa() {
	    	String ss = Serializer.serializePlain(MockObjectUtil.OFFER);
	    	System.out.println(ss);
	    	String o = "{\r\n"
	    			+ "  \"consumerPid\" : null,\r\n"
	    			+ "  \"providerPid\" : null,\r\n"
	    			+ "  \"id\" : \"fdc45798-a123-4955-8baf-ab7fd66ac4d5\",\r\n"
	    			+ "  \"target\" : \"urn:uuid:TARGET\",\r\n"
	    			+ "  \"assigner\" : \"urn:uuid:ASSIGNER_PROVIDER\",\r\n"
	    			+ "  \"assignee\" : null,\r\n"
	    			+ "  \"permission\" : [ {\r\n"
	    			+ "    \"assigner\" : null,\r\n"
	    			+ "    \"assignee\" : null,\r\n"
	    			+ "    \"target\" : \"urn:uuid:TARGET\",\r\n"
	    			+ "    \"action\" : \"use\",\r\n"
	    			+ "    \"constraint\" : [ {\r\n"
	    			+ "      \"leftOperand\" : \"COUNT\",\r\n"
	    			+ "      \"operator\" : \"EQ\",\r\n"
	    			+ "      \"rightOperand\" : \"5\"\r\n"
	    			+ "    } ]\r\n"
	    			+ "  } ]\r\n"
	    			+ "}";
	    
	    Offer of = Serializer.deserializePlain(ss, Offer.class);
	    assertNotNull(of);
	    }

}
