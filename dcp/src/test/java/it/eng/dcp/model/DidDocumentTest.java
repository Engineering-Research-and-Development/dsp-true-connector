package it.eng.dcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DidDocumentTest {

    DidDocument didDocument = DidDocument.Builder.newInstance()
            .id("did:web:localhost%3A8083:holder")
            .service(List.of(
                    new ServiceEntry("TCK-Credential-Service", "CredentialService", "http://localhost:8083")
            ))
            .verificationMethod(List.of(
                    VerificationMethod.Builder.newInstance()
                            .id("43cecd95-7a59-4a5f-b2d0-0ec73ae41a0c")
                            .type("JsonWebKey2020")
                            .controller("did:web:localhost%3A8083:holder")
                            .publicKeyJwk(Map.of(
                                    "kty", "EC",
                                    "use", "sig",
                                    "crv", "P-256",
                                    "kid", "43cecd95-7a59-4a5f-b2d0-0ec73ae41a0c",
                                    "x", "izxXHDdzCpmt_Ivvn19qOZVhLDE29ViWPJENBJeEncA",
                                    "y", "dbn4rBSbYFbyUSbt0GzfKpxK4eODNRkWaI5LB36P9MY"
                            ))
                            .build()
            ))
            .build();

    @Test
    public void testDidDocumentSerialization() {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            // Serialize
            String jsonString = objectMapper.writeValueAsString(didDocument);

            // Basic checks on serialized string
            assertNotNull(jsonString, "Serialized JSON should not be null");
            assertTrue(jsonString.contains("did:web:localhost%3A8083:holder"), "Serialized JSON should contain DID id");
            assertTrue(jsonString.contains("TCK-Credential-Service"), "Serialized JSON should contain service id");
            // check presence of key type in JSON
            assertTrue(jsonString.contains("\"kty\""), "Serialized JSON should contain 'kty' field");
            assertTrue(jsonString.contains("EC"), "Serialized JSON should contain 'EC' curve value");

            // Deserialize and verify object fields
            DidDocument deserializedDocument = objectMapper.readValue(jsonString, DidDocument.class);
            assertNotNull(deserializedDocument, "Deserialized DidDocument should not be null");
            assertEquals("did:web:localhost%3A8083:holder", deserializedDocument.getId(), "DID id should match");

            assertNotNull(deserializedDocument.getServices(), "Services should not be null");
            assertEquals(1, deserializedDocument.getServices().size(), "There should be one service");
            ServiceEntry service = deserializedDocument.getServices().get(0);
            assertEquals("TCK-Credential-Service", service.id());

            assertNotNull(deserializedDocument.getVerificationMethods(), "Verification methods should not be null");
            assertEquals(1, deserializedDocument.getVerificationMethods().size(), "There should be one verification method");
            VerificationMethod vm = deserializedDocument.getVerificationMethods().get(0);
            assertEquals("JsonWebKey2020", vm.getType(), "Verification method type should match");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
