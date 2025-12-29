package it.eng.dcp.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CredentialOfferMessageTest {

    @Test
    @DisplayName("Builder constructs CredentialOfferMessage and JSON roundtrip preserves fields")
    void builderConstructsOfferAndRoundtripPreservesFields() throws Exception {
        CredentialOfferMessage.CredentialObject offered = CredentialOfferMessage.CredentialObject.Builder.newInstance()
                .credentialType("ProofCredential")
                .issuancePolicy(Map.of("pol", true))
                .build();

        CredentialOfferMessage msg = CredentialOfferMessage.Builder.newInstance()
                .offeredCredentials(List.of(offered))
                .issuer("did:example:issuer123")
                .build();

        assertEquals(CredentialOfferMessage.class.getSimpleName(), msg.getType());
        assertNotNull(msg.getCredentialObjects());
        assertEquals("did:example:issuer123", msg.getIssuer());
        assertEquals(1, msg.getCredentialObjects().size());

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(msg);
        assertNotNull(json);
        assertTrue(json.contains("ProofCredential"));

        CredentialOfferMessage deserialized = mapper.readValue(json, CredentialOfferMessage.class);
        assertNotNull(deserialized);
        assertEquals(msg.getType(), deserialized.getType());
        assertEquals("did:example:issuer123", deserialized.getIssuer());
        assertEquals(msg.getCredentialObjects().size(), deserialized.getCredentialObjects().size());
    }

    @Test
    @DisplayName("Builder constructs valid OfferedCredential and JSON roundtrip preserves all mandatory fields")
    void builderConstructsValidOfferedCredentialAndRoundtripPreservesFields() throws Exception {
        CredentialOfferMessage.CredentialObject offered = CredentialOfferMessage.CredentialObject.Builder.newInstance()
                .id("cred-1234")
                .credentialType("ProofCredential")
                .issuancePolicy(Map.of("policy", true))
                .bindingMethods(List.of("binding1", "binding2"))
                .profile("profile1")
                .offerReason("reason1")
                .credentialSchema("schema1")
                .build();

        assertEquals("cred-1234", offered.getId());
        assertEquals("ProofCredential", offered.getCredentialType());
        assertEquals(Map.of("policy", true), offered.getIssuancePolicy());
        assertEquals(List.of("binding1", "binding2"), offered.getBindingMethods());
        assertEquals("profile1", offered.getProfile());
        assertEquals(CredentialOfferMessage.CredentialObject.class.getSimpleName(), offered.getType());
        assertEquals("reason1", offered.getOfferReason());
        assertEquals("schema1", offered.getCredentialSchema());

        CredentialOfferMessage msg = CredentialOfferMessage.Builder.newInstance()
                .offeredCredentials(List.of(offered))
                .issuer("did:example:issuer123")
                .build();

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(msg);
        assertNotNull(json);
        assertTrue(json.contains("cred-1234"));
        assertTrue(json.contains("ProofCredential"));
        assertTrue(json.contains("binding1"));
        assertTrue(json.contains("profile1"));
        assertTrue(json.contains(CredentialOfferMessage.CredentialObject.class.getSimpleName()));
        assertTrue(json.contains("reason1"));
        assertTrue(json.contains("schema1"));

        CredentialOfferMessage deserialized = mapper.readValue(json, CredentialOfferMessage.class);
        assertNotNull(deserialized);
        assertEquals(1, deserialized.getCredentialObjects().size());
        CredentialOfferMessage.CredentialObject deserializedOffered = deserialized.getCredentialObjects().get(0);
        assertEquals(offered.getId(), deserializedOffered.getId());
        assertEquals(offered.getCredentialType(), deserializedOffered.getCredentialType());
        assertEquals(offered.getIssuancePolicy(), deserializedOffered.getIssuancePolicy());
        assertEquals(offered.getBindingMethods(), deserializedOffered.getBindingMethods());
        assertEquals(offered.getProfile(), deserializedOffered.getProfile());
        assertEquals(offered.getType(), deserializedOffered.getType());
        assertEquals(offered.getOfferReason(), deserializedOffered.getOfferReason());
        assertEquals(offered.getCredentialSchema(), deserializedOffered.getCredentialSchema());
    }
}
