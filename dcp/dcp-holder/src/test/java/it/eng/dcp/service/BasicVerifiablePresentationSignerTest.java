package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.holder.model.VerifiablePresentation;
import it.eng.dcp.holder.service.BasicVerifiablePresentationSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class BasicVerifiablePresentationSignerTest {

    @Mock
    private KeyService keyService;
    @Mock
    private DidDocumentConfig config;

    private BasicVerifiablePresentationSigner signer;
    private final ObjectMapper mapper = new ObjectMapper();
    private ECKey testKey;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Generate a test EC key for signing
        testKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key-id")
                .generate();

        // Mock KeyService to return test key
        when(keyService.getSigningJwk(config)).thenReturn(testKey);

        signer = new BasicVerifiablePresentationSigner(keyService, config);
    }

    @Test
    void testJwtFormatProducesSignedJwt() throws Exception {
        // Arrange
        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                .holderDid("did:web:localhost:8080")
                .credentialIds(List.of("urn:uuid:test-cred-1", "urn:uuid:test-cred-2"))
                .profileId(ProfileId.VC11_SL2021_JWT)
                .build();

        // Act
        Object result = signer.sign(vp, "jwt");

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof String, "JWT should be a string");

        String jwt = (String) result;
        System.out.println("Generated Signed JWT: " + jwt);

        // Signed JWT has 3 parts: header.payload.signature
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "Signed JWT should have 3 parts");

        // Parse as SignedJWT to verify it's properly signed
        SignedJWT signedJWT = SignedJWT.parse(jwt);

        // Verify header
        assertEquals("ES256", signedJWT.getHeader().getAlgorithm().getName(), "Algorithm should be ES256");
        assertEquals("test-key-id", signedJWT.getHeader().getKeyID(), "Key ID should match");

        // Verify payload contains VP claim
        assertNotNull(signedJWT.getJWTClaimsSet().getClaim("vp"), "Should contain vp claim");
        assertEquals(vp.getHolderDid(), signedJWT.getJWTClaimsSet().getIssuer(), "Issuer should be holder DID");
        assertEquals(vp.getId(), signedJWT.getJWTClaimsSet().getJWTID(), "JTI should be VP ID");

        // Verify signature is not empty
        assertFalse(parts[2].isEmpty(), "Signature should not be empty");

        // Verify the signature is valid
        boolean isValid = signedJWT.verify(new com.nimbusds.jose.crypto.ECDSAVerifier(testKey.toECPublicKey()));
        assertTrue(isValid, "JWT signature should be valid");

        System.out.println("\n✅ JWT is properly signed with ES256 and can be verified!");
    }

    @Test
    void testJwtContainsVpClaimStructure() throws Exception {
        // Arrange
        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                .holderDid("did:web:example.com")
                .credentialIds(List.of("urn:uuid:credential-123"))
                .profileId(ProfileId.VC11_SL2021_JWT)
                .build();

        // Act
        String jwt = (String) signer.sign(vp, "jwt");
        SignedJWT signedJWT = SignedJWT.parse(jwt);

        // Assert - Check VP claim structure
        Object vpClaim = signedJWT.getJWTClaimsSet().getClaim("vp");
        assertNotNull(vpClaim, "VP claim should be present");

        // Convert to JSON for inspection
        String vpJson = mapper.writeValueAsString(vpClaim);
        System.out.println("VP Claim: " + vpJson);

        JsonNode vpNode = mapper.readTree(vpJson);
        assertTrue(vpNode.has("@context"), "VP should have @context");
        assertTrue(vpNode.has("type"), "VP should have type");
        assertTrue(vpNode.has("verifiableCredential"), "VP should have verifiableCredential");

        assertEquals("VerifiablePresentation", vpNode.get("type").get(0).asText(),
                    "Type should be VerifiablePresentation");

        System.out.println("✅ VP claim structure is correct per W3C VC Data Model");
    }

    @Test
    void testJsonLdFormat() {
        // Arrange
        VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                .holderDid("did:web:localhost:8080")
                .credentialIds(List.of("urn:uuid:test-cred-1"))
                .profileId(ProfileId.VC20_BSSL_JWT)
                .build();

        // Act
        Object result = signer.sign(vp, "json-ld");

        // Assert
        assertNotNull(result);
        assertTrue(result instanceof com.fasterxml.jackson.databind.node.ObjectNode,
                   "JSON-LD should be an ObjectNode");
    }
}

