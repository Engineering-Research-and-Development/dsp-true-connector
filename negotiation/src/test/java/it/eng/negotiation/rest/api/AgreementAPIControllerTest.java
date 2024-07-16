package it.eng.negotiation.rest.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.ModelUtil;
import it.eng.negotiation.service.ContractNegotiationAPIService;
import it.eng.tools.response.GenericApiResponse;

@ExtendWith(MockitoExtension.class)
class AgreementAPIControllerTest {

	@Mock
	private ContractNegotiationAPIService contractNegotiationAPIService;
	
	@InjectMocks
	private AgreementAPIController controller;
	
	@Test
	@DisplayName("Validate agreement")
	void testValidateAgreement() {
		doNothing().when(contractNegotiationAPIService).validateAgreement(any(String.class));
		ResponseEntity<GenericApiResponse<String>> response = controller.validateAgreement(ModelUtil.AGREEMENT.getId());
		assertNotNull(response);
		assertTrue(response.getBody().isSuccess());
	}
	
	@Test
	@DisplayName("Validate agreement - not valid")
	void testValidateAgreement_serviceError() {
		doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
		.when(contractNegotiationAPIService).validateAgreement(any(String.class));
		assertThrows(ContractNegotiationAPIException.class, 
				() -> controller.validateAgreement(ModelUtil.AGREEMENT.getId()));
	}

}
