package it.eng.dataplane.httppull.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code DataPlaneAuditEventController} in the HTTP-PULL Data Plane.
 *
 * <p>Verifies pagination, event-type listing, single-event lookup, and that
 * starting a data flow produces auditable records.
 */
class AuditEventControllerIT extends BaseHttpPullIT {

    private static final String AUDIT_URL = "/api/v1/audit";

    @Test
    @DisplayName("GET /api/v1/audit with API key returns 200 with paged result fields")
    void getAuditEvents_returnsPagedResult() throws Exception {
        mockMvc.perform(withApiKey(get(AUDIT_URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    @DisplayName("GET /api/v1/audit without API key returns 401 or 403")
    void getAuditEvents_withoutApiKey_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(AUDIT_URL))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /api/v1/audit/{id} with non-existent id returns 404")
    void getAuditEventById_notFound_returns404() throws Exception {
        mockMvc.perform(withApiKey(get(AUDIT_URL + "/nonexistent-id-000")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/audit/types returns 200 with event type content")
    void getAuditEventTypes_returnsTypes() throws Exception {
        mockMvc.perform(withApiKey(get(AUDIT_URL + "/types")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());
    }

    @Test
    @DisplayName("Starting a DataFlow creates audit events visible via GET /api/v1/audit")
    void startDataFlow_createsAuditEvents() throws Exception {
        String processId = newId();
        Map<String, Object> startBody = Map.of(
                "processId", processId,
                "transferType", "HttpData-PULL"
        );

        mockMvc.perform(withApiKey(post("/dataflows/start"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(startBody)))
                .andExpect(status().isCreated());

        Thread.sleep(200);

        mockMvc.perform(withApiKey(get(AUDIT_URL)
                .param("processId", processId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThan(0)));
    }
}
