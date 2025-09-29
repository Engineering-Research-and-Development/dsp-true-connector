package it.eng.negotiation.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.negotiation.exception.ContractNegotiationAPIException;
import it.eng.negotiation.exception.ContractNegotiationNotFoundException;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.negotiation.service.ContractNegotiationAPIService;
import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.model.IConstants;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.rest.api.PagedAPIResponse;
import it.eng.tools.service.GenericFilterBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ContractNegotiationAPIControllerTest {

    @Mock
    private ContractNegotiationAPIService apiService;
    @Mock
    private GenericFilterBuilder filterBuilder;
    @Mock
    private PagedResourcesAssembler<ContractNegotiation> pagedResourcesAssembler;
    @Mock
    private PlainContractNegotiationAssembler plainAssembler;
    @Mock
    private Pageable pageable;

    @InjectMocks
    private ContractNegotiationAPIController controller;

    ObjectMapper mapper = new ObjectMapper();

    private Page<ContractNegotiation> contractNegotiationPage;

    PagedModel.PageMetadata metadata = new PagedModel.PageMetadata(20, 0, 2, 1);

    @Test
    @DisplayName("Find all contract negotiations")
    public void findAll() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Map<String, Object> expectedFilters = new HashMap<>();

        List<EntityModel<ContractNegotiation>> content = Arrays.asList(
                EntityModel.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED),
                EntityModel.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED));
        PagedModel<EntityModel<ContractNegotiation>> pagedModel = PagedModel.of(content, metadata);
        contractNegotiationPage = new PageImpl<>(Arrays.asList(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED,
                NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED),
                pageable, 2);

        when(filterBuilder.buildFromRequest(any(HttpServletRequest.class))).thenReturn(expectedFilters);
        when(apiService.findContractNegotiations(anyMap(), any(Pageable.class))).thenReturn(contractNegotiationPage);
        when(pagedResourcesAssembler.toModel(contractNegotiationPage, plainAssembler)).thenReturn((PagedModel) pagedModel);

        ResponseEntity<PagedAPIResponse> response = controller.getContractNegotiations(request, 0, 20, new String[]{"timestamp", "desc"});

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getResponse().isSuccess());
        assertFalse(response.getBody().getResponse().getData().getContent().isEmpty());
    }

    @Test
    @DisplayName("Find contract negotiations by state")
    public void findContractNegotiationByState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", ContractNegotiationState.ACCEPTED.name());

        Map<String, Object> expectedFilters = Map.of(
                "state", ContractNegotiationState.ACCEPTED.name()
        );

        List<EntityModel<ContractNegotiation>> content = Collections.singletonList(
                EntityModel.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));
        PagedModel<EntityModel<ContractNegotiation>> pagedModel = PagedModel.of(content, metadata);
        contractNegotiationPage = new PageImpl<>(Collections.singletonList(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED),
                pageable, 1);

        when(filterBuilder.buildFromRequest(any(HttpServletRequest.class))).thenReturn(expectedFilters);
        when(apiService.findContractNegotiations(anyMap(), any(Pageable.class))).thenReturn(contractNegotiationPage);
        when(pagedResourcesAssembler.toModel(contractNegotiationPage, plainAssembler)).thenReturn((PagedModel) pagedModel);

        ResponseEntity<PagedAPIResponse> response = controller.getContractNegotiations(request, 0, 20, new String[]{"timestamp", "desc"});

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getResponse().isSuccess());
        assertFalse(response.getBody().getResponse().getData().getContent().isEmpty());
    }

    @Test
    @DisplayName("Find contract negotiations by id")
    public void findContractNegotiationById() {
        when(apiService.findContractNegotiationById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED);

        ResponseEntity<GenericApiResponse<JsonNode>> response =
                controller.getContractNegotiationById(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId());

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNotNull(response.getBody().getData());
        assertNotNull(NegotiationSerializer.deserializePlain(response.getBody().getData().toPrettyString(), ContractNegotiation.class));
    }

    @Test
    @DisplayName("Find contract negotiations by pids")
    public void findContractNegotiationByPids() {
        String consumerPid = "consumer-pid";
        String providerPid = "provider-pid";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(IConstants.CONSUMER_PID, consumerPid);
        request.setParameter(IConstants.PROVIDER_PID, providerPid);

        Map<String, Object> expectedFilters = Map.of(
                IConstants.CONSUMER_PID, consumerPid,
                IConstants.PROVIDER_PID, providerPid);

        List<EntityModel<ContractNegotiation>> content = Collections.singletonList(
                EntityModel.of(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));
        PagedModel<EntityModel<ContractNegotiation>> pagedModel = PagedModel.of(content, metadata);
        contractNegotiationPage = new PageImpl<>(Collections.singletonList(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED),
                pageable, 1);

        when(filterBuilder.buildFromRequest(any(HttpServletRequest.class))).thenReturn(expectedFilters);
        when(apiService.findContractNegotiations(anyMap(), any(Pageable.class))).thenReturn(contractNegotiationPage);
        when(pagedResourcesAssembler.toModel(contractNegotiationPage, plainAssembler)).thenReturn((PagedModel) pagedModel);

        ResponseEntity<PagedAPIResponse> response = controller.getContractNegotiations(request, 0, 20, new String[]{"timestamp", "desc"});

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getResponse().isSuccess());
        assertFalse(response.getBody().getResponse().getData().getContent().isEmpty());
    }

    @Test
    @DisplayName("Start contract negotiation success")
    public void startNegotiation_success() {
        Map<String, Object> map = new HashMap<>();
        map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
        map.put("offer", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));

        when(apiService.startNegotiation(any(String.class), any(JsonNode.class)))
                .thenReturn(NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED));

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.startNegotiation(mapper.convertValue(map, JsonNode.class));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).startNegotiation(any(String.class), any(JsonNode.class));
    }

    @Test
    @DisplayName("Verify negotiation success")
    public void verifyNegotiation_success() {

        ResponseEntity<GenericApiResponse<Void>> response = controller.verifyContractNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).verifyNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
    }

    @Test
    @DisplayName("Verify negotiation failed")
    public void verifyNegotiation_failed() {

        doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
                .when(apiService).verifyNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());

        assertThrows(ContractNegotiationAPIException.class, () ->
                controller.verifyContractNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));
    }

    @Test
    @DisplayName("Provider posts offer - success")
    public void providerPostsOffer_success() {
        Map<String, Object> map = new HashMap<>();
        map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
        map.put("offer", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));

        when(apiService.sendContractOffer(any(String.class), any(JsonNode.class)))
                .thenReturn(NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED));
        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.sendContractOffer(mapper.convertValue(map, JsonNode.class));

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(response.getBody().getData().get(DSpaceConstants.TYPE).asText(), ContractNegotiation.class.getSimpleName());
    }

    @Test
    @DisplayName("Provider posts offer - error")
    public void providerPostsOffer_error() {
        Map<String, Object> map = new HashMap<>();
        map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
        map.put("offer", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));

        when(apiService.sendContractOffer(any(String.class), any(JsonNode.class)))
                .thenThrow(new ContractNegotiationAPIException("Something not correct - tests"));

        assertThrows(ContractNegotiationAPIException.class, () ->
                controller.sendContractOffer(mapper.convertValue(map, JsonNode.class)));
    }

    @Test
    @DisplayName("Send agreement success")
    public void sendAgreement_success() {
        Map<String, Object> map = new HashMap<>();
        map.put("consumerPid", NegotiationMockObjectUtil.CONSUMER_PID);
        map.put("providerPid", NegotiationMockObjectUtil.PROVIDER_PID);
        map.put("agreement", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.AGREEMENT));

        ResponseEntity<GenericApiResponse<Void>> response = controller.sendAgreement(mapper.convertValue(map, JsonNode.class));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).sendAgreement(any(String.class), any(String.class), any(JsonNode.class));
    }

    @Test
    @DisplayName("Send agreement failed")
    public void sendAgreement_failed() {
        Map<String, Object> map = new HashMap<>();
        map.put("consumerPid", NegotiationMockObjectUtil.CONSUMER_PID);
        map.put("providerPid", NegotiationMockObjectUtil.PROVIDER_PID);
        map.put("agreement", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.AGREEMENT));

        doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
                .when(apiService).sendAgreement(any(String.class), any(String.class), any(JsonNode.class));

        assertThrows(ContractNegotiationAPIException.class, () ->
                controller.sendAgreement(mapper.convertValue(map, JsonNode.class)));
    }

    @Test
    @DisplayName("Finalize negotiation success")
    public void finalizeNegotiation_success() {

        ResponseEntity<GenericApiResponse<Void>> response = controller.finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
    }

    @Test
    @DisplayName("Finalize negotiation failed")
    public void finalizeNegotiation_failed() {

        doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
                .when(apiService).finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());

        assertThrows(ContractNegotiationAPIException.class, () ->
                controller.finalizeNegotiation(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));
    }

    @Test
    @DisplayName("Provider approves negotation")
    public void providerAcceptsCN() {
        String contractNegotaitionId = UUID.randomUUID().toString();
        when(apiService.approveContractNegotiation(contractNegotaitionId))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED);

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.approveContractNegotiation(contractNegotaitionId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Provider accepts negotation - service throws error")
    public void providerAcceptsCN_error() {
        String contractNegotaitionId = UUID.randomUUID().toString();
        when(apiService.approveContractNegotiation(contractNegotaitionId))
                .thenThrow(ContractNegotiationNotFoundException.class);

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> controller.approveContractNegotiation(contractNegotaitionId));
    }

    @Test
    @DisplayName("Provider terminates negotation")
    public void providerTerminatesCN() {
        String contractNegotaitionId = UUID.randomUUID().toString();
        when(apiService.handleContractNegotiationTerminated(contractNegotaitionId))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_TERMINATED);

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.terminateContractNegotiation(contractNegotaitionId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Provider terminates negotation - service error")
    public void providerTerminatesCN_error() {
        String contractNegotaitionId = UUID.randomUUID().toString();
        when(apiService.handleContractNegotiationTerminated(contractNegotaitionId))
                .thenThrow(ContractNegotiationNotFoundException.class);

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> controller.terminateContractNegotiation(contractNegotaitionId));
    }

    @Test
    @DisplayName("Consumer accepts negotiation offered by provider")
    public void handleContractNegotationAccepted() {
        String contractNegotaitionId = UUID.randomUUID().toString();
        when(apiService.handleContractNegotiationAccepted(contractNegotaitionId)).thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED);

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.acceptContractNegotiation(contractNegotaitionId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Consumer acepts negotiation offered by provider - error while processing")
    public void handleContractNegotationAccepted_service_error() {
        String contractNegotaitionId = UUID.randomUUID().toString();
        when(apiService.handleContractNegotiationAccepted(contractNegotaitionId))
                .thenThrow(ContractNegotiationNotFoundException.class);

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> controller.acceptContractNegotiation(contractNegotaitionId));
    }
}
