package it.eng.dcp.common.model;

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

