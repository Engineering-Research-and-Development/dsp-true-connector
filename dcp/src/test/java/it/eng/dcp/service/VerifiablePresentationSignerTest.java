package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.model.VerifiablePresentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class VerifiablePresentationSignerTest {

    @Mock
    private KeyService keyService;
    
    private BasicVerifiablePresentationSigner signer;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // Generate a test EC key for signing
        ECKey testKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key-id")
                .generate();
        
        // Mock KeyService to return test key
        when(keyService.getSigningJwk()).thenReturn(testKey);
        
        signer = new BasicVerifiablePresentationSigner(keyService);
    }

    @Test
    void jwtFormatProducesCompactString() {
        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                .holderDid("did:example:holder")
                .credentialIds(java.util.List.of("cred1", "cred2"))
                .profileId("vc11-jwt")
                .build();

        Object out = signer.sign(vp, "jwt");
        assertInstanceOf(String.class, out, "JWT format should return a string");
        String jwt = (String) out;
        assertEquals(3, jwt.split("\\.").length, "JWT compact format should have three dot-separated parts");
    }

    @Test
    void jsonLdFormatReturnsObjectWithProof() {
        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                .holderDid("did:example:holder")
                .credentialIds(java.util.List.of("cred1"))
                .profileId("vc11-jsonld")
                .build();

        Object out = signer.sign(vp, "json-ld");
        assertNotNull(out);
        JsonNode node = mapper.convertValue(out, JsonNode.class);
        assertTrue(node.has("proof"), "JSON-LD presentation should include a proof block");
        JsonNode proof = node.get("proof");
        assertTrue(proof.has("type"));
        assertTrue(proof.has("created"));
    }
}
