package it.eng.connector.integration.connector;

import it.eng.connector.integration.BaseIntegrationTest;
import it.eng.connector.rest.api.DSpaceVersionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class DspaceVersionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DSpaceVersionController versionController;

    @Test
    @DisplayName("Verify version response is correct")
    public void verifyVersionsResponse() {
        var response = versionController.getVersion();
        System.out.println(response);
        assert response != null;
        assert response.getProtocolVersions() != null;
        assert !response.getProtocolVersions().isEmpty();
        var version = response.getProtocolVersions().get(0);
        assert "2024-01".equals(version.getVersion());
        assert "/path/to/api".equals(version.getPath());
        assert version.getAuth() != null;
        assert "https".equals(version.getAuth().getProtocol());
        assert "2024-01".equals(version.getAuth().getVersion());
    }

    @Test
    @DisplayName("Verify mockMvc works")
    public void verifyMockMvcWorks() {
        try {
            mockMvc.perform(get("/.well-known/dspace-version"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(jsonPath("$.protocolVersions").isArray())
                    .andExpect(jsonPath("$.protocolVersions[0].version").value("2024-01"))
                    .andExpect(jsonPath("$.protocolVersions[0].path").value("/path/to/api"))
                    .andExpect(jsonPath("$.protocolVersions[0].auth.protocol").value("https"))
                    .andExpect(jsonPath("$.protocolVersions[0].auth.version").value("2024-01"));
        } catch (Exception e) {
            assert false : "MockMvc request failed";
        }
    }
}
