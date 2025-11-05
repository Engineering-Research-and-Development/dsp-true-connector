package it.eng.dcp.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VerificationMethodTest {

    @Test
    @DisplayName("Builder sets fields and publicKeyJwk map")
    void builderSetsFieldsAndPublicKeyJwk() {
        Map<String, Object> jwk = Map.of(
                "kty", "EC",
                "use", "sig",
                "crv", "P-256",
                "x", "abc",
                "y", "def"
        );

        VerificationMethod vm = VerificationMethod.Builder.newInstance()
                .id("did:example:123#key-1")
                .type("JsonWebKey2020")
                .controller("did:example:123")
                .publicKeyMultibase("z6Mkp...")
                .publicKeyJwk(jwk)
                .build();

        assertEquals("did:example:123#key-1", vm.getId());
        assertEquals("JsonWebKey2020", vm.getType());
        assertEquals("did:example:123", vm.getController());
        assertEquals("z6Mkp...", vm.getPublicKeyMultibase());
        assertNotNull(vm.getPublicKeyJwk());
        assertEquals("EC", vm.getPublicKeyJwk().get("kty"));
        assertEquals("abc", vm.getPublicKeyJwk().get("x"));
    }

    @Test
    @DisplayName("JSON round-trip preserves all fields")
    void jsonRoundtripPreservesFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> jwk = Map.of(
                "kty", "EC",
                "use", "sig",
                "crv", "P-256",
                "x", "abc",
                "y", "def"
        );

        VerificationMethod vm = VerificationMethod.Builder.newInstance()
                .id("did:example:456#key-2")
                .type("JsonWebKey2020")
                .controller("did:example:456")
                .publicKeyJwk(jwk)
                .build();

        String json = mapper.writeValueAsString(vm);
        VerificationMethod deserialized = mapper.readValue(json, VerificationMethod.class);

        assertEquals(vm.getId(), deserialized.getId());
        assertEquals(vm.getType(), deserialized.getType());
        assertEquals(vm.getController(), deserialized.getController());
        assertEquals(vm.getPublicKeyJwk(), deserialized.getPublicKeyJwk());
    }
}
