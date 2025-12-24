package it.eng.dcp.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.model.IssuerMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IssuerMetadataTest {

    @Test
    @DisplayName("Builder constructs IssuerMetadata and JSON roundtrip preserves fields")
    void builderConstructsIssuerMetadataAndJsonRoundtrip() throws Exception {
        IssuerMetadata.CredentialObject co = IssuerMetadata.CredentialObject.Builder.newInstance()
                .id("cred-1")
                .type("CredentialObject")
                .credentialType("ProofCredential")
                .credentialSchema("https://example.com/schema")
                .profile("vc11-jwt")
                .issuancePolicy(Map.of("allow", true))
                .bindingMethods(List.of("device-bound"))
                .build();

        IssuerMetadata meta = IssuerMetadata.Builder.newInstance()
                .issuer("did:web:example:issuer")
                .credentialsSupported(List.of(co))
                .build();

        assertEquals("IssuerMetadata", meta.getType());
        assertEquals("did:web:example:issuer", meta.getIssuer());
        assertEquals(1, meta.getCredentialsSupported().size());

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(meta);
        assertNotNull(json);
        assertTrue(json.contains("cred-1"));

        IssuerMetadata deserialized = mapper.readValue(json, IssuerMetadata.class);
        assertNotNull(deserialized);
        assertEquals(meta.getType(), deserialized.getType());
        assertEquals(meta.getIssuer(), deserialized.getIssuer());
        assertEquals(meta.getCredentialsSupported().size(), deserialized.getCredentialsSupported().size());
    }

    @Test
    @DisplayName("Duplicate credential ids are rejected")
    void duplicateCredentialIdsRejected() {
        IssuerMetadata.CredentialObject co1 = IssuerMetadata.CredentialObject.Builder.newInstance()
                .id("dup")
                .type("CredentialObject")
                .credentialType("Proof")
                .build();

        IssuerMetadata.CredentialObject co2 = IssuerMetadata.CredentialObject.Builder.newInstance()
                .id("dup")
                .type("CredentialObject")
                .credentialType("Proof2")
                .build();

        Exception ex = assertThrows(Exception.class, () ->
                IssuerMetadata.Builder.newInstance()
                        .issuer("did:web:example:issuer")
                        .credentialsSupported(List.of(co1, co2))
                        .build()
        );

        assertTrue(ex.getMessage().contains("duplicate credential id"));
    }
}

