package it.eng.negotiation.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.model.Agreement;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.AgreementAPIService;
import it.eng.tools.response.GenericApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgreementAPIControllerTest {

    @Mock
    private AgreementAPIService agreementAPIService;
    @Mock
    private PagedResourcesAssembler<Agreement> pagedResourcesAssembler;
    @Mock
    private PlainAgreementAssembler plainAssembler;
    @Mock
    private Pageable pageable;

    @InjectMocks
    private AgreementAPIController controller;

    @Test
    @DisplayName("Get agreement by id - success")
    void getAgreementById() {
        JsonNode agreementJson = NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.AGREEMENT);
        when(agreementAPIService.findAgreementByIdEnriched(NegotiationMockObjectUtil.AGREEMENT.getId()))
                .thenReturn(agreementJson);

        ResponseEntity<GenericApiResponse<JsonNode>> response =
                controller.getAgreementById(NegotiationMockObjectUtil.AGREEMENT.getId());

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getData());
    }

    @Test
    @DisplayName("Get agreement by id - not found")
    void getAgreementById_notFound() {
        when(agreementAPIService.findAgreementByIdEnriched(any(String.class)))
                .thenThrow(new ContractNegotiationAPIException("Agreement not found"));

        assertThrows(ContractNegotiationAPIException.class,
                () -> controller.getAgreementById(NegotiationMockObjectUtil.AGREEMENT.getId()));
    }

    @Test
    @DisplayName("Enforce agreement - success")
    void enforceAgreement() {
        doNothing().when(agreementAPIService).enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId());

        ResponseEntity<GenericApiResponse<String>> response =
                controller.enforceAgreement(NegotiationMockObjectUtil.AGREEMENT.getId());

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
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
