package it.eng.connector.integration.multitenant;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.negotiation.model.ContractNegotiation;
import it.eng.negotiation.model.ContractNegotiationState;
import it.eng.negotiation.model.NegotiationMockObjectUtil;
import it.eng.negotiation.repository.ContractNegotiationRepository;
import it.eng.negotiation.serializer.NegotiationSerializer;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant isolation of the contract negotiation API ({@code /api/v1/negotiations}).
 *
 * <p>Each tenant has a consumer and a provider negotiation. A tenant admin lists and reads only its own; every
 * read or action on the other tenant's negotiation is answered with "not found" (400, the module's not-found
 * status), sends nothing to the counterparty and leaves the negotiation unchanged.
 */
public class NegotiationApiTenantIsolationIT extends AbstractTenantIsolationIT {

    /** Callback of tenant B's negotiations; a request reaching it would mean the action ran. */
    private static final String CALLBACK_B = "http://localhost:1/tenant-b-callback";

    @Autowired
    private ContractNegotiationRepository contractNegotiationRepository;

    private ContractNegotiation consumerA;
    private ContractNegotiation providerA;
    private ContractNegotiation consumerB;
    private ContractNegotiation providerB;

    @BeforeEach
    void seedNegotiations() {
        consumerA = save(TENANT_A, IConstants.ROLE_CONSUMER, ContractNegotiationState.OFFERED, "http://localhost:1/tenant-a");
        providerA = save(TENANT_A, IConstants.ROLE_PROVIDER, ContractNegotiationState.REQUESTED, "http://localhost:1/tenant-a");
        consumerB = save(TENANT_B, IConstants.ROLE_CONSUMER, ContractNegotiationState.OFFERED, CALLBACK_B);
        providerB = save(TENANT_B, IConstants.ROLE_PROVIDER, ContractNegotiationState.REQUESTED, CALLBACK_B);
    }

    @AfterEach
    void cleanup() {
        for (ContractNegotiation negotiation : List.of(consumerA, providerA, consumerB, providerB)) {
            contractNegotiationRepository.deleteById(negotiation.getId());
        }
    }

    @Test
    @DisplayName("GET /negotiations lists only the tenant's own negotiations")
    void list_returnsOnlyOwn() throws Exception {
        MvcResult resultA = mockMvc.perform(get(ApiEndpoints.NEGOTIATION_V1).with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        Set<String> idsA = ids(pagedContent(resultA));
        assertTrue(idsA.containsAll(Set.of(consumerA.getId(), providerA.getId())), "own negotiations listed: " + idsA);
        assertBodyDoesNotContain(resultA, consumerB.getId(), providerB.getId(), CALLBACK_B);

        MvcResult resultB = mockMvc.perform(get(ApiEndpoints.NEGOTIATION_V1).with(adminOf(TENANT_B)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(Set.of(consumerB.getId(), providerB.getId()), ids(pagedContent(resultB)));
    }

    @Test
    @DisplayName("GET /negotiations with filters (role, providerPid) never returns the other tenant's negotiation")
    void listWithFilters_returnsOnlyOwn() throws Exception {
        MvcResult byRole = mockMvc.perform(get(ApiEndpoints.NEGOTIATION_V1)
                        .param("role", IConstants.ROLE_PROVIDER)
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertTrue(ids(pagedContent(byRole)).contains(providerA.getId()));
        assertBodyDoesNotContain(byRole, providerB.getId());

        MvcResult byOtherPid = mockMvc.perform(get(ApiEndpoints.NEGOTIATION_V1)
                        .param("providerPid", providerB.getProviderPid())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(0, pagedContent(byOtherPid).size());
    }

    @Test
    @DisplayName("GET /negotiations/{id}: own negotiation found, other tenant's negotiation not found")
    void getById_ownFound_otherNotFound() throws Exception {
        MvcResult own = mockMvc.perform(get(ApiEndpoints.NEGOTIATION_V1 + "/" + consumerA.getId()).with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(consumerA.getId(), data(own).path("@id").asText());

        MvcResult other = mockMvc.perform(get(ApiEndpoints.NEGOTIATION_V1 + "/" + consumerB.getId()).with(adminOf(TENANT_A)))
                .andExpect(status().isBadRequest()).andReturn();
        assertNotFound(other);
        assertBodyDoesNotContain(other, CALLBACK_B, consumerB.getProviderPid());
    }

    @ParameterizedTest(name = "PUT /negotiations/'{id}'/{0} on the other tenant's negotiation")
    @ValueSource(strings = {"accept", "verify", "agree", "finalize", "terminate"})
    @DisplayName("State-changing actions on the other tenant's negotiation are not found and change nothing")
    void actions_onOtherTenantNegotiation_notFoundAndUnchanged(String action) throws Exception {
        for (ContractNegotiation target : List.of(consumerB, providerB)) {
            MvcResult result = mockMvc.perform(put(ApiEndpoints.NEGOTIATION_V1 + "/" + target.getId() + "/" + action)
                            .with(adminOf(TENANT_A))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest()).andReturn();
            assertNotFound(result);
            assertUnchanged(target);
        }
    }

    @ParameterizedTest(name = "PUT /negotiations/'{id}'/{0} (counteroffer) on the other tenant's negotiation")
    @ValueSource(strings = {"request", "offer"})
    @DisplayName("Counteroffers on the other tenant's negotiation are not found and change nothing")
    void counteroffers_onOtherTenantNegotiation_notFoundAndUnchanged(String action) throws Exception {
        String offer = NegotiationSerializer.serializePlain(NegotiationMockObjectUtil.OFFER);
        for (ContractNegotiation target : List.of(consumerB, providerB)) {
            MvcResult result = mockMvc.perform(put(ApiEndpoints.NEGOTIATION_V1 + "/" + target.getId() + "/" + action)
                            .with(adminOf(TENANT_A))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(offer))
                    .andExpect(status().isBadRequest()).andReturn();
            assertNotFound(result);
            assertUnchanged(target);
        }
    }

    private ContractNegotiation save(String tenantId, String role, ContractNegotiationState state, String callback) {
        return contractNegotiationRepository.save(ContractNegotiation.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .callbackAddress(callback)
                .assigner(NegotiationMockObjectUtil.ASSIGNER)
                .offer(NegotiationMockObjectUtil.OFFER)
                .role(role)
                .state(state)
                .tenantId(tenantId)
                .build());
    }

    private void assertNotFound(MvcResult result) throws Exception {
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(body.path("message").asText().contains("not found"),
                "expected a not-found answer but was: " + result.getResponse().getContentAsString());
    }

    private void assertUnchanged(ContractNegotiation expected) {
        ContractNegotiation stored = contractNegotiationRepository.findById(expected.getId()).orElseThrow();
        assertEquals(expected.getState(), stored.getState());
        assertEquals(expected.getVersion(), stored.getVersion());
        assertEquals(TENANT_B, stored.getTenantId());
    }
}
