package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantKeyProvider;
import it.eng.dcp.common.participant.ParticipantState;
import it.eng.dcp.common.participant.SigningKeyReference;
import it.eng.dcp.holder.model.VerifiablePresentation;
import it.eng.dcp.holder.service.BasicVerifiablePresentationSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class VerifiablePresentationSignerTest {

    @Mock
    private ParticipantKeyProvider keyProvider;

    private ParticipantContext participant;
    
    private BasicVerifiablePresentationSigner signer;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        participant = new ParticipantContext("default", "did:web:localhost:8080", new SigningKeyReference("eckey.p12", "alias"),
                List.of(), Map.of(), null, ParticipantState.ENABLED);
        
        // Generate a test EC key for signing
        ECKey testKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key-id")
                .generate();
        
        // Mock key provider to return test key
        when(keyProvider.activeSigningKey(participant)).thenReturn(testKey);
        
        signer = new BasicVerifiablePresentationSigner(keyProvider);
    }

    @Test
    void jwtFormatProducesCompactString() {
        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                .holderDid("did:example:holder")
                .credentialIds(java.util.List.of("cred1", "cred2"))
                .profileId(ProfileId.VC20_BSSL_JWT)
                .build();

        Object out = signer.sign(vp, "jwt", participant);
        assertInstanceOf(String.class, out, "JWT format should return a string");
        String jwt = (String) out;
        assertEquals(3, jwt.split("\\.").length, "JWT compact format should have three dot-separated parts");
    }

    @Test
    void jsonLdFormatReturnsObjectWithProof() {
        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                .holderDid("did:example:holder")
                .credentialIds(java.util.List.of("cred1"))
                .profileId(ProfileId.VC11_SL2021_JWT)
                .build();

        Object out = signer.sign(vp, "json-ld", participant);
        assertNotNull(out);
        JsonNode node = mapper.convertValue(out, JsonNode.class);
        assertTrue(node.has("proof"), "JSON-LD presentation should include a proof block");
        JsonNode proof = node.get("proof");
        assertTrue(proof.has("type"));
        assertTrue(proof.has("created"));
    }
}
