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
    public void sendContractRequestMessage_success() {
        Map<String, Object> map = new HashMap<>();
        map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
        map.put("offer", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));

        when(apiService.sendContractRequestMessage(any(JsonNode.class)))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED);

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.sendContractRequestMessage(mapper.convertValue(map, JsonNode.class));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).sendContractRequestMessage(any(JsonNode.class));
    }

    @Test
    @DisplayName("Send contract request message as counteroffer - success")
    public void sendContractRequestMessageAsCounteroffer_success() {
        JsonNode counterofferNode = NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.OFFER);

        when(apiService.sendContractRequestMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId(), counterofferNode))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED);

        ResponseEntity<GenericApiResponse<JsonNode>> response =
                controller.sendContractRequestMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId(), counterofferNode);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Counter offer sent", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        verify(apiService).sendContractRequestMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId(), counterofferNode);
    }

    @Test
    @DisplayName("Send contract request message as counteroffer - negotiation not found")
    public void sendContractRequestMessageAsCounteroffer_notFound() {
        JsonNode counterofferNode = NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.OFFER);

        when(apiService.sendContractRequestMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId(), counterofferNode))
                .thenThrow(new ContractNegotiationNotFoundException("Contract negotiation with id: " + NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId() + " not found"));

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> controller.sendContractRequestMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId(), counterofferNode));
        verify(apiService).sendContractRequestMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId(), counterofferNode);
    }

    @Test
    @DisplayName("Send contract request message as counteroffer - API exception")
    public void sendContractRequestMessageAsCounteroffer_apiException() {
        JsonNode counterofferNode = NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.OFFER);

        when(apiService.sendContractRequestMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId(), counterofferNode))
                .thenThrow(new ContractNegotiationAPIException("Invalid counteroffer"));

        assertThrows(ContractNegotiationAPIException.class,
                () -> controller.sendContractRequestMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId(), counterofferNode));
        verify(apiService).sendContractRequestMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED.getId(), counterofferNode);
    }

    @Test
    @DisplayName("Consumer accepts negotiation offered by provider")
    public void handleContractNegotiationAccepted() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(apiService.sendContractNegotiationEventMessageAccepted(contractNegotiationId)).thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED);

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.sendContractNegotiationEventMessageAccepted(contractNegotiationId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Consumer accepts negotiation offered by provider - error while processing")
    public void handleContractNegotiationAccepted_service_error() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(apiService.sendContractNegotiationEventMessageAccepted(contractNegotiationId))
                .thenThrow(ContractNegotiationNotFoundException.class);

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> controller.sendContractNegotiationEventMessageAccepted(contractNegotiationId));
    }

    @Test
    @DisplayName("Verify negotiation success")
    public void sendContractAgreementVerificationMessage_success() {

        ResponseEntity<GenericApiResponse<Void>> response = controller.sendContractAgreementVerificationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).sendContractAgreementVerificationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
    }

    @Test
    @DisplayName("Verify negotiation failed")
    public void sendContractAgreementVerificationMessage_failed() {

        doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
                .when(apiService).sendContractAgreementVerificationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());

        assertThrows(ContractNegotiationAPIException.class, () ->
                controller.sendContractAgreementVerificationMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));
    }

    @Test
    @DisplayName("Provider posts offer - success")
    public void sendContractOfferMessage_success() {
        Map<String, Object> map = new HashMap<>();
        map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
        map.put("offer", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));

        when(apiService.sendContractOfferMessage(any(JsonNode.class)))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED);
        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.sendContractOfferMessage(mapper.convertValue(map, JsonNode.class));

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
    }

    @Test
    @DisplayName("Provider posts offer - error")
    public void providerPostsOffer_error() {
        Map<String, Object> map = new HashMap<>();
        map.put("Forward-To", NegotiationMockObjectUtil.FORWARD_TO);
        map.put("offer", NegotiationSerializer.serializeProtocolJsonNode(NegotiationMockObjectUtil.OFFER));

        when(apiService.sendContractOfferMessage(any(JsonNode.class)))
                .thenThrow(new ContractNegotiationAPIException("Something not correct - tests"));

        assertThrows(ContractNegotiationAPIException.class, () ->
                controller.sendContractOfferMessage(mapper.convertValue(map, JsonNode.class)));
    }

    @Test
    @DisplayName("Send contract offer message as counteroffer - success")
    public void sendContractOfferMessageAsCounteroffer_success() {
        JsonNode counterofferNode = NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.OFFER);

        when(apiService.sendContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId(), counterofferNode))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_OFFERED);

        ResponseEntity<GenericApiResponse<JsonNode>> response =
                controller.sendContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId(), counterofferNode);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Counter offer sent", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        verify(apiService).sendContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId(), counterofferNode);
    }

    @Test
    @DisplayName("Send contract offer message as counteroffer - negotiation not found")
    public void sendContractOfferMessageAsCounteroffer_notFound() {
        JsonNode counterofferNode = NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.OFFER);

        when(apiService.sendContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId(), counterofferNode))
                .thenThrow(new ContractNegotiationNotFoundException("Contract negotiation with id: " + NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId() + " not found"));

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> controller.sendContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId(), counterofferNode));
        verify(apiService).sendContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId(), counterofferNode);
    }

    @Test
    @DisplayName("Send contract offer message as counteroffer - API exception")
    public void sendContractOfferMessageAsCounteroffer_apiException() {
        JsonNode counterofferNode = NegotiationSerializer.serializePlainJsonNode(NegotiationMockObjectUtil.OFFER);

        when(apiService.sendContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId(), counterofferNode))
                .thenThrow(new ContractNegotiationAPIException("New offer must have same offer id and target as the existing one"));

        assertThrows(ContractNegotiationAPIException.class,
                () -> controller.sendContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId(), counterofferNode));
        verify(apiService).sendContractOfferMessageAsCounteroffer(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_REQUESTED.getId(), counterofferNode);
    }

    @Test
    @DisplayName("Send agreement success")
    public void sendAgreement_success() {
        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.sendContractAgreementMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).sendContractAgreementMessage(any(String.class));
    }

    @Test
    @DisplayName("Send agreement failed")
    public void sendAgreement_failed() {
        doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
                .when(apiService).sendContractAgreementMessage(any(String.class));

        assertThrows(ContractNegotiationAPIException.class, () ->
                controller.sendContractAgreementMessage(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_ACCEPTED.getId()));
    }

    @Test
    @DisplayName("Provider approves negotiation")
    public void providerAcceptsCN() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(apiService.sendContractAgreementMessage(contractNegotiationId))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_AGREED);

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.sendContractAgreementMessage(contractNegotiationId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Provider accepts negotiation - service throws error")
    public void providerAcceptsCN_error() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(apiService.sendContractAgreementMessage(contractNegotiationId))
                .thenThrow(ContractNegotiationNotFoundException.class);

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> controller.sendContractAgreementMessage(contractNegotiationId));
    }

    @Test
    @DisplayName("Finalize negotiation success")
    public void sendContractNegotiation_EventMessageFinalize_success() {
        ResponseEntity<GenericApiResponse<Void>> response = controller.sendContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(apiService).sendContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());
    }

    @Test
    @DisplayName("Finalize negotiation failed")
    public void sendContractNegotiation_EventMessageFinalize_failed() {

        doThrow(new ContractNegotiationAPIException("Something not correct - tests"))
                .when(apiService).sendContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId());

        assertThrows(ContractNegotiationAPIException.class, () ->
                controller.sendContractNegotiationEventMessageFinalize(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_VERIFIED.getId()));
    }

    @Test
    @DisplayName("Provider terminates negotiation")
    public void providerTerminatesCN() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(apiService.sendContractNegotiationTerminationMessage(contractNegotiationId))
                .thenReturn(NegotiationMockObjectUtil.CONTRACT_NEGOTIATION_TERMINATED);

        ResponseEntity<GenericApiResponse<JsonNode>> response = controller.sendContractNegotiationTerminationMessage(contractNegotiationId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("Provider terminates negotiation - service error")
    public void providerTerminatesCN_error() {
        String contractNegotiationId = UUID.randomUUID().toString();
        when(apiService.sendContractNegotiationTerminationMessage(contractNegotiationId))
                .thenThrow(ContractNegotiationNotFoundException.class);

        assertThrows(ContractNegotiationNotFoundException.class,
                () -> controller.sendContractNegotiationTerminationMessage(contractNegotiationId));
    }
}
