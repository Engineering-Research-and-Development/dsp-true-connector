package it.eng.dcp.common.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DidDocumentTest {

    @Test
    void testDidDocumentBuilder_Success() {
        DidDocument doc = DidDocument.Builder.newInstance()
                .id("did:web:example.com")
                .service(List.of(new ServiceEntry("service-1", "CredentialService", "https://example.com/credentials")))
                .verificationMethod(List.of(
                        VerificationMethod.Builder.newInstance()
                                .id("did:web:example.com#key-1")
                                .type("JsonWebKey2020")
                                .controller("did:web:example.com")
                                .build()
                ))
                .build();

        assertNotNull(doc);
        assertEquals("did:web:example.com", doc.getId());
        assertEquals(1, doc.getServices().size());
        assertEquals(1, doc.getVerificationMethods().size());
        assertEquals("service-1", doc.getServices().get(0).id());
    }

    @Test
    void verificationRelationships_serializedWhenPresentAndOmittedWhenEmpty() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        DidDocument doc = DidDocument.Builder.newInstance()
                .id("did:web:example.com")
                .authentication(List.of("did:web:example.com#key-1"))
                .capabilityInvocation(List.of("did:web:example.com#key-1"))
                .build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(doc));

        assertEquals("did:web:example.com#key-1", json.get("authentication").get(0).asText());
        assertEquals("did:web:example.com#key-1", json.get("capabilityInvocation").get(0).asText());
        assertFalse(json.has("assertionMethod"));
    }

    @Test
    void verificationRelationships_deserializeReferencesAndEmbeddedMethods() throws Exception {
        String json = """
                {
                  "id": "did:web:example.com",
                  "verificationMethod": [{"id": "did:web:example.com#key-1", "type": "JsonWebKey2020"}],
                  "authentication": ["#key-1"],
                  "capabilityInvocation": [
                    "did:web:example.com#key-1",
                    {"id": "did:web:example.com#key-2", "type": "JsonWebKey2020"}
                  ],
                  "assertionMethod": ["did:web:example.com#key-1"]
                }
                """;

        DidDocument doc = new ObjectMapper().readValue(json, DidDocument.class);

        assertEquals(List.of("#key-1"), doc.getAuthentication());
        assertEquals(List.of("did:web:example.com#key-1", "did:web:example.com#key-2"), doc.getCapabilityInvocation());
        assertEquals(List.of("did:web:example.com#key-1"), doc.getAssertionMethod());
    }

    @Test
    void verificationRelationships_absentInJson_areEmpty() throws Exception {
        DidDocument doc = new ObjectMapper().readValue("{\"id\": \"did:web:example.com\"}", DidDocument.class);

        assertTrue(doc.getAuthentication().isEmpty());
        assertTrue(doc.getCapabilityInvocation().isEmpty());
        assertTrue(doc.getAssertionMethod().isEmpty());
    }

    @Test
    void testDidDocumentBuilder_MissingId() {
        assertThrows(Exception.class, () -> {
            DidDocument.Builder.newInstance().build();
        });
    }

    @Test
    void testServiceEntry_Creation() {
        ServiceEntry entry = new ServiceEntry("svc-1", "CredentialIssuer", "https://issuer.com/issue");

        assertEquals("svc-1", entry.id());
        assertEquals("CredentialIssuer", entry.type());
        assertEquals("https://issuer.com/issue", entry.serviceEndpoint());
    }
}

