package it.eng.negotiation.rest.internal;

import io.swagger.v3.oas.annotations.Hidden;
import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.service.AgreementAPIService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class InternalAgreementControllerTest {

	@Mock
	private AgreementAPIService agreementAPIService;
	
	@InjectMocks
	private InternalAgreementController controller;
	
	@Test
	@DisplayName("The controller is mapped under /internal/v1/agreements, consumes JSON and is hidden from the OpenAPI docs")
	void mappedUnderInternalZoneAndHidden() throws NoSuchMethodException {
		assertArrayEquals(new String[]{ApiEndpoints.INTERNAL_AGREEMENTS_V1},
				InternalAgreementController.class.getAnnotation(RequestMapping.class).path());
		assertNotNull(InternalAgreementController.class.getAnnotation(Hidden.class));
		PostMapping enforce = InternalAgreementController.class
				.getDeclaredMethod("enforceAgreement", String.class).getAnnotation(PostMapping.class);
		assertArrayEquals(new String[]{"/{agreementId}/enforce"}, enforce.path());
		assertArrayEquals(new String[]{MediaType.APPLICATION_JSON_VALUE}, enforce.consumes());
	}

	@Test
	@DisplayName("Enforce agreement")
	void enforceAgreement() {
		doNothing().when(agreementAPIService).enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId());
		ResponseEntity<GenericApiResponse<String>> response = controller.enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId());
		assertNotNull(response);
		assertTrue(response.getBody().isSuccess());
	}
	
	@Test
	@DisplayName("Enforce agreement - not valid")
	void enforceAgreement_serviceError() {
		doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
		.when(agreementAPIService).enforceAgreement(any(String.class));
		assertThrows(ContractNegotiationAPIException.class, 
				() -> controller.enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId()));
	}
}
