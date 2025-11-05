package it.eng.dsp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.model.DidDocument;
import it.eng.dcp.model.ServiceEntry;
import it.eng.dcp.model.VerificationMethod;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
            System.out.println("Serialized JSON: " + jsonString);

            // Deserialize
            DidDocument deserializedDocument = objectMapper.readValue(jsonString, DidDocument.class);
            System.out.println("Deserialized DID Document ID: " + deserializedDocument.getId());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
