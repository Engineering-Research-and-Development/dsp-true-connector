package it.eng.connector.integration.datatransfer;

import com.github.tomakehurst.wiremock.client.WireMock;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.util.TestUtil;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.IConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the canonical Data Plane → Control Plane callback endpoints.
 *
 * <p>These tests exercise the canonical per-transfer callback paths introduced in the
 * HTTP pull/push dataplane alignment work:
 * <ul>
 *   <li>{@code POST /api/v1/transfers/{id}/dataflow/prepared}</li>
 *   <li>{@code POST /api/v1/transfers/{id}/dataflow/started}</li>
 *   <li>{@code POST /api/v1/transfers/{id}/dataflow/completed}</li>
 *   <li>{@code POST /api/v1/transfers/{id}/dataflow/errored}</li>
 * </ul>
 *
 * <p>Each callback is authenticated via the {@code X-Api-Key} header that matches a
 * registered Data Plane entry. All callbacks require the Data Plane to be registered
 * in MongoDB with the key {@link BaseIntegrationTest#DP_API_KEY} — done automatically
 * by {@code BaseIntegrationTest.ensureTestDataPlanes()}.</p>
 *
 * <p>Callbacks that trigger peer DSP messages ({@code completed}, {@code errored}) use
 * WireMock to stub the peer connector endpoint.</p>
 */
public class DataPlaneProtocolAlignmentIT extends BaseIntegrationTest {

    @Autowired
    private TransferProcessRepository transferProcessRepository;

    @AfterEach
    public void cleanup() {
        transferProcessRepository.deleteAll();
    }

    // ── Canonical callback: started ───────────────────────────────────────────

    @Test
    @DisplayName("Canonical started callback — persists internal dataflow state STARTED")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void startedCallback_canonicalPath_persistsDataFlowState() throws Exception {
        TransferProcess tp = buildStartedTransferProcess(null);
        transferProcessRepository.save(tp);

        String body = buildStatusMessageJson(tp.getId(), DataFlowState.STARTED, null);

        mockMvc.perform(
                        post(ApiEndpoints.DATAFLOW_CALLBACK_STARTED.replace("{processId}", tp.getId()))
                                .header("X-Api-Key", DP_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());

        TransferProcess updated = transferProcessRepository.findById(tp.getId()).orElseThrow();
        assertEquals("STARTED", updated.getDataFlowState());
    }

    // ── Canonical callback: prepared ──────────────────────────────────────────

    @Test
    @DisplayName("Canonical prepared callback — persists internal dataflow state PREPARED")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void preparedCallback_canonicalPath_persistsDataFlowState() throws Exception {
        TransferProcess tp = buildStartedTransferProcess(null);
        transferProcessRepository.save(tp);

        String body = buildStatusMessageJson(tp.getId(), DataFlowState.PREPARED, null);

        mockMvc.perform(
                        post(ApiEndpoints.DATAFLOW_CALLBACK_PREPARED.replace("{processId}", tp.getId()))
                                .header("X-Api-Key", DP_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());

        TransferProcess updated = transferProcessRepository.findById(tp.getId()).orElseThrow();
        assertEquals("PREPARED", updated.getDataFlowState());
    }

    // ── Canonical callback: completed ─────────────────────────────────────────

    @Test
    @DisplayName("Canonical completed callback — marks transfer COMPLETED via canonical path")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void completedCallback_canonicalPath_marksTransferCompleted() throws Exception {
        TransferProcess tp = buildStartedTransferProcess(wireMock.baseUrl());
        transferProcessRepository.save(tp);

        // Stub the peer connector's completion endpoint (provider calls consumer on completion)
        WireMock.stubFor(WireMock.post(
                        WireMock.urlMatching("/transfers/" + tp.getConsumerPid() + "/completion"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)));

        String body = buildStatusMessageJson(tp.getId(), DataFlowState.COMPLETED, null);

        mockMvc.perform(
                        post(ApiEndpoints.DATAFLOW_CALLBACK_COMPLETED.replace("{processId}", tp.getId()))
                                .header("X-Api-Key", DP_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());

        TransferProcess updated = transferProcessRepository.findById(tp.getId()).orElseThrow();
        assertNotNull(updated);
        assertEquals(TransferState.COMPLETED, updated.getState());
    }

    // ── Canonical callback: errored ───────────────────────────────────────────

    @Test
    @DisplayName("Canonical errored callback — terminates transfer via canonical path")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void erroredCallback_canonicalPath_terminatesTransfer() throws Exception {
        TransferProcess tp = buildStartedTransferProcess(wireMock.baseUrl());
        transferProcessRepository.save(tp);

        // Stub the peer connector's termination endpoint (provider calls consumer on termination)
        WireMock.stubFor(WireMock.post(
                        WireMock.urlMatching("/transfers/" + tp.getConsumerPid() + "/termination"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)));

        String body = buildStatusMessageJson(tp.getId(), DataFlowState.TERMINATED, "DP encountered an error");

        mockMvc.perform(
                        post(ApiEndpoints.DATAFLOW_CALLBACK_ERRORED.replace("{processId}", tp.getId()))
                                .header("X-Api-Key", DP_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());

        TransferProcess updated = transferProcessRepository.findById(tp.getId()).orElseThrow();
        assertNotNull(updated);
        assertEquals(TransferState.TERMINATED, updated.getState());
    }

    // ── Authentication: missing / invalid API key ─────────────────────────────

    @Test
    @DisplayName("Canonical completed callback — missing X-Api-Key returns 401")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void completedCallback_missingApiKey_returns401() throws Exception {
        TransferProcess tp = buildStartedTransferProcess(null);
        transferProcessRepository.save(tp);

        String body = buildStatusMessageJson(tp.getId(), DataFlowState.COMPLETED, null);

        mockMvc.perform(
                        post(ApiEndpoints.DATAFLOW_CALLBACK_COMPLETED.replace("{processId}", tp.getId()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Canonical completed callback — unknown X-Api-Key returns 401")
    @WithUserDetails(TestUtil.CONNECTOR_USER)
    public void completedCallback_unknownApiKey_returns401() throws Exception {
        TransferProcess tp = buildStartedTransferProcess(null);
        transferProcessRepository.save(tp);

        String body = buildStatusMessageJson(tp.getId(), DataFlowState.COMPLETED, null);

        mockMvc.perform(
                        post(ApiEndpoints.DATAFLOW_CALLBACK_COMPLETED.replace("{processId}", tp.getId()))
                                .header("X-Api-Key", "totally-unknown-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a STARTED provider-role {@link TransferProcess} using the given callback address.
     * Pass {@code null} for {@code callbackAddress} when the test does not require a peer HTTP call.
     *
     * @param callbackAddress the callback address for peer DSP messages; may be {@code null}
     * @return the unsaved {@link TransferProcess} instance
     */
    private TransferProcess buildStartedTransferProcess(String callbackAddress) {
        TransferProcess.Builder builder = TransferProcess.Builder.newInstance()
                .consumerPid(createNewId())
                .providerPid(createNewId())
                .format(DataTransferFormat.HTTP_PULL.format())
                .state(TransferState.STARTED)
                .role(IConstants.ROLE_PROVIDER)
                .tenantId(TENANT_ID);
        if (callbackAddress != null) {
            builder.callbackAddress(callbackAddress);
        }
        return builder.build();
    }

    /**
     * Serializes a minimal {@code DataFlowStatusMessage} JSON body for use in callback requests.
     *
     * @param processId    the transfer process ID (used as {@code processId} in the message body)
     * @param state        the {@link DataFlowState} to embed
     * @param errorMessage optional error message; {@code null} to omit the field
     * @return a JSON string representing the message
     */
    private String buildStatusMessageJson(String processId, DataFlowState state, String errorMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"processId\":\"").append(processId).append("\"");
        sb.append(",\"state\":\"").append(state.name()).append("\"");
        if (errorMessage != null) {
            sb.append(",\"errorMessage\":\"").append(errorMessage).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }
}
