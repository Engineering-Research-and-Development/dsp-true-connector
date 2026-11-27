package it.eng.connector.integration.multitenant;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.util.DataTransferMockObjectUtil;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant isolation of the data transfer API ({@code /api/v1/transfers}).
 *
 * <p>Each tenant has transfer processes in the states the actions start from. A tenant admin lists and reads
 * only its own; every read, action, download or view on the other tenant's transfer process is answered with
 * "not found" (400, the module's not-found status) and leaves the process unchanged.
 */
public class TransferApiTenantIsolationIT extends AbstractTenantIsolationIT {

    private static final String CALLBACK_B = "http://localhost:1/tenant-b-callback";

    @Autowired
    private TransferProcessRepository transferProcessRepository;

    private TransferProcess startedA;
    private TransferProcess initializedB;
    private TransferProcess requestedB;
    private TransferProcess startedB;

    @BeforeEach
    void seedTransfers() {
        startedA = save(TENANT_A, IConstants.ROLE_CONSUMER, TransferState.STARTED, "http://localhost:1/tenant-a");
        initializedB = save(TENANT_B, IConstants.ROLE_CONSUMER, TransferState.INITIALIZED, CALLBACK_B);
        requestedB = save(TENANT_B, IConstants.ROLE_PROVIDER, TransferState.REQUESTED, CALLBACK_B);
        startedB = save(TENANT_B, IConstants.ROLE_CONSUMER, TransferState.STARTED, CALLBACK_B);
    }

    @AfterEach
    void cleanup() {
        for (TransferProcess process : List.of(startedA, initializedB, requestedB, startedB)) {
            transferProcessRepository.deleteById(process.getId());
        }
    }

    @Test
    @DisplayName("GET /transfers lists only the tenant's own transfer processes")
    void list_returnsOnlyOwn() throws Exception {
        MvcResult resultA = mockMvc.perform(get(ApiEndpoints.TRANSFER_DATATRANSFER_V1).with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertTrue(ids(pagedContent(resultA)).contains(startedA.getId()));
        assertBodyDoesNotContain(resultA, initializedB.getId(), requestedB.getId(), startedB.getId(), CALLBACK_B);

        MvcResult resultB = mockMvc.perform(get(ApiEndpoints.TRANSFER_DATATRANSFER_V1).with(adminOf(TENANT_B)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(Set.of(initializedB.getId(), requestedB.getId(), startedB.getId()), ids(pagedContent(resultB)));
    }

    @Test
    @DisplayName("GET /transfers with filters (state, consumerPid) never returns the other tenant's transfer process")
    void listWithFilters_returnsOnlyOwn() throws Exception {
        MvcResult byState = mockMvc.perform(get(ApiEndpoints.TRANSFER_DATATRANSFER_V1)
                        .param("state", TransferState.STARTED.name())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertTrue(ids(pagedContent(byState)).contains(startedA.getId()));
        assertBodyDoesNotContain(byState, startedB.getId());

        MvcResult byOtherPid = mockMvc.perform(get(ApiEndpoints.TRANSFER_DATATRANSFER_V1)
                        .param("consumerPid", startedB.getConsumerPid())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(0, pagedContent(byOtherPid).size());
    }

    @Test
    @DisplayName("GET /transfers/{id}: own transfer process found, other tenant's not found")
    void getById_ownFound_otherNotFound() throws Exception {
        MvcResult own = mockMvc.perform(get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + startedA.getId())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isOk()).andReturn();
        assertEquals(startedA.getId(), data(own).path("@id").asText());

        MvcResult other = mockMvc.perform(get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + startedB.getId())
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isBadRequest()).andReturn();
        assertNotFound(other);
        assertBodyDoesNotContain(other, CALLBACK_B, startedB.getConsumerPid());
    }

    @Test
    @DisplayName("PUT /transfers/{id}/start on the other tenant's requested transfer is not found and changes nothing")
    void start_onOtherTenantTransfer_notFoundAndUnchanged() throws Exception {
        MvcResult result = mockMvc.perform(put(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + requestedB.getId() + "/start")
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isBadRequest()).andReturn();
        assertNotFound(result);
        assertUnchanged(requestedB);
    }

    @ParameterizedTest(name = "PUT /transfers/'{id}'/{0} on the other tenant's started transfer")
    @ValueSource(strings = {"complete", "suspend", "terminate"})
    @DisplayName("Actions on the other tenant's started transfer are not found and change nothing")
    void actions_onOtherTenantTransfer_notFoundAndUnchanged(String action) throws Exception {
        MvcResult result = mockMvc.perform(put(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + startedB.getId() + "/" + action)
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isBadRequest()).andReturn();
        assertNotFound(result);
        assertUnchanged(startedB);
    }

    @ParameterizedTest(name = "GET /transfers/'{id}'/{0} on the other tenant's started transfer")
    @ValueSource(strings = {"download", "view"})
    @DisplayName("Download and view of the other tenant's transfer are not found and change nothing")
    void downloadAndView_onOtherTenantTransfer_notFoundAndUnchanged(String action) throws Exception {
        MvcResult result = mockMvc.perform(get(ApiEndpoints.TRANSFER_DATATRANSFER_V1 + "/" + startedB.getId() + "/" + action)
                        .with(adminOf(TENANT_A)))
                .andExpect(status().isBadRequest()).andReturn();
        assertNotFound(result);
        assertUnchanged(startedB);
    }

    @Test
    @DisplayName("POST /transfers for the other tenant's initialized transfer is not found and changes nothing")
    void requestTransfer_forOtherTenantTransfer_notFoundAndUnchanged() throws Exception {
        String body = jsonMapper.writeValueAsString(Map.of(
                "transferProcessId", initializedB.getId(),
                "format", DataTransferFormat.HTTP_PULL.format()));

        MvcResult result = mockMvc.perform(post(ApiEndpoints.TRANSFER_DATATRANSFER_V1)
                        .with(adminOf(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest()).andReturn();
        assertNotFound(result);
        assertUnchanged(initializedB);
    }

    private TransferProcess save(String tenantId, String role, TransferState state, String callback) {
        return transferProcessRepository.save(TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .agreementId(createNewId())
                .callbackAddress(callback)
                .dataAddress(DataTransferMockObjectUtil.DATA_ADDRESS)
                .format(DataTransferFormat.HTTP_PULL.format())
                .role(role)
                .state(state)
                .isDownloaded(false)
                .isDownloadInProgress(false)
                .tenantId(tenantId)
                .build());
    }

    private void assertNotFound(MvcResult result) throws Exception {
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(body.path("message").asText().contains("not found"),
                "expected a not-found answer but was: " + result.getResponse().getContentAsString());
    }

    private void assertUnchanged(TransferProcess expected) {
        TransferProcess stored = transferProcessRepository.findById(expected.getId()).orElseThrow();
        assertEquals(expected.getState(), stored.getState());
        assertEquals(expected.getVersion(), stored.getVersion());
        assertEquals(expected.isDownloaded(), stored.isDownloaded());
        assertEquals(TENANT_B, stored.getTenantId());
    }
}
