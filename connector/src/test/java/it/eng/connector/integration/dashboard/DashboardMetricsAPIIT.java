package it.eng.connector.integration.dashboard;

import com.fasterxml.jackson.core.type.TypeReference;
import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.model.dashboard.DashboardSummaryResponse;
import it.eng.connector.util.TestUtil;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.serializer.ToolsSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DashboardMetricsAPIIT extends BaseIntegrationTest {

    @Test
    @DisplayName("GET /api/v1/dashboard/summary returns dashboard sections for admin requests")
    @WithUserDetails(TestUtil.ADMIN_USER)
    public void getSummary_asAdmin_returnsDashboardSections() throws Exception {
        MvcResult result = mockMvc.perform(get(ApiEndpoints.DASHBOARD_SUMMARY_V1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        TypeReference<GenericApiResponse<DashboardSummaryResponse>> typeReference =
                new TypeReference<GenericApiResponse<DashboardSummaryResponse>>() {
                };
        GenericApiResponse<DashboardSummaryResponse> apiResponse = ToolsSerializer.deserializePlain(
                result.getResponse().getContentAsString(),
                typeReference
        );

        assertTrue(apiResponse.isSuccess());
        assertNotNull(apiResponse.getData());
        assertNotNull(apiResponse.getData().negotiations());
        assertNotNull(apiResponse.getData().transfers());
        assertNotNull(apiResponse.getData().events());
        assertNotNull(apiResponse.getData().runtime());
        assertTrue(apiResponse.getData().runtime().liveThreadCount() > 0);
        assertTrue(apiResponse.getData().runtime().uptimeMilliseconds() >= 0L);
    }
}
