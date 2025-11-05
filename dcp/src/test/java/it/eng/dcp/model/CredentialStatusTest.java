package it.eng.dcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CredentialStatusTest {

    @Test
    @DisplayName("Terminal states behave as expected")
    void terminalStatesBehaveAsExpected() {
        assertFalse(CredentialStatus.PENDING.isTerminal());
        assertTrue(CredentialStatus.ISSUED.isTerminal());
        assertTrue(CredentialStatus.REJECTED.isTerminal());
    }

    @Test
    @DisplayName("JSON roundtrip for enum")
    void jsonRoundtripForEnum() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(CredentialStatus.ISSUED);
        assertNotNull(json);
        assertTrue(json.contains("ISSUED"));

        CredentialStatus des = mapper.readValue(json, CredentialStatus.class);
        assertEquals(CredentialStatus.ISSUED, des);
    }
}

