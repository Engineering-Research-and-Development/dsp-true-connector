package it.eng.dcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceEntryTest {

    @Test
    @DisplayName("ServiceEntry record stores values and JSON round-trip preserves them")
    void serviceEntryStoresValuesAndJsonRoundtripPreservesThem() throws Exception {
        ServiceEntry entry = new ServiceEntry("svc-1", "CredentialService", "https://example.com/api");

        assertEquals("svc-1", entry.id());
        assertEquals("CredentialService", entry.type());
        assertEquals("https://example.com/api", entry.serviceEndpoint());

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(entry);

        assertNotNull(json, "Serialized JSON should not be null");
        assertTrue(json.contains("svc-1"), "JSON should contain id");
        assertTrue(json.contains("CredentialService"), "JSON should contain type");
        assertTrue(json.contains("https://example.com/api"), "JSON should contain serviceEndpoint");

        ServiceEntry deserialized = mapper.readValue(json, ServiceEntry.class);
        assertNotNull(deserialized, "Deserialized ServiceEntry should not be null");
        assertEquals(entry.id(), deserialized.id());
        assertEquals(entry.type(), deserialized.type());
        assertEquals(entry.serviceEndpoint(), deserialized.serviceEndpoint());
    }
}

