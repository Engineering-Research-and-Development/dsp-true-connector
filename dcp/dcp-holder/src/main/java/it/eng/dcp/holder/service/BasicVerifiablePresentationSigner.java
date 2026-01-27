package it.eng.dcp.holder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.holder.model.VerifiablePresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class BasicVerifiablePresentationSigner implements VerifiablePresentationSigner {

    private final ObjectMapper mapper = new ObjectMapper();
    private final KeyService keyService;
    private final DidDocumentConfig config;

    @Autowired
    public BasicVerifiablePresentationSigner(KeyService keyService,
                                             @Qualifier("holderDidDocumentConfig") DidDocumentConfig config) {
        this.keyService = keyService;
        this.config = config;
    }

    @Override
    public Object sign(VerifiablePresentation vp, String format) {
        if (format == null) format = "json-ld";
        switch (format.toLowerCase()) {
            case "jwt":
                // Produce a signed JWT VP using ES256 algorithm with the holder's key
                try {
                    // Get the signing key from KeyService
                    ECKey signingKey = keyService.getSigningJwk(config);

                    // Build JWT claims for Verifiable Presentation
                    JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                            .claim("vp", buildVPClaim(vp))
                            .issuer(vp.getHolderDid())
                            .subject(vp.getHolderDid())
                            .issueTime(new Date())
                            .jwtID(vp.getId())
                            .build();

                    // Create JWS header with key ID
                    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                            .keyID(signingKey.getKeyID())
                            .build();

                    // Create signed JWT
                    SignedJWT signedJWT = new SignedJWT(header, claimsSet);

                    // Sign the JWT
                    JWSSigner signer = new ECDSASigner(signingKey);
                    signedJWT.sign(signer);

                    return signedJWT.serialize();
                } catch (JOSEException e) {
                    throw new RuntimeException("Failed to sign JWT VP", e);
                }
            case "json-ld":
                // Produce a signed JSON-LD VP with JsonWebSignature2020 proof
                try {
                    // Get the signing key from KeyService
                    ECKey signingKey = keyService.getSigningJwk(config);

                    // Build the presentation document
                    ObjectNode presentation = mapper.createObjectNode();
                    presentation.putArray("@context")
                            .add("https://www.w3.org/2018/credentials/v1")
                            .add("https://w3id.org/security/suites/jws-2020/v1");
                    presentation.put("id", vp.getId());
                    presentation.putArray("type").add("VerifiablePresentation");
                    presentation.putPOJO("verifiableCredential", vp.getCredentialIds());
                    presentation.put("holder", vp.getHolderDid());

                    if (vp.getProfileId() != null) {
                        presentation.put("profileId", vp.getProfileId().getSpecAlias());
                    }

                    // Create the proof using JWS (detached signature)
                    // Per W3C VC Data Model, we use JsonWebSignature2020
                    String verificationMethod = vp.getHolderDid() + "#" + signingKey.getKeyID();
                    String created = java.time.Instant.now().toString();

                    // Create canonical representation for signing
                    String canonicalPresentation = mapper.writeValueAsString(presentation);
                    byte[] dataToSign = canonicalPresentation.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                    // Sign using ECDSA
                    JWSSigner signer = new ECDSASigner(signingKey);
                    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                            .base64URLEncodePayload(false)
                            .criticalParams(java.util.Set.of("b64"))
                            .build();

                    com.nimbusds.jose.JWSObject jwsObject = new com.nimbusds.jose.JWSObject(
                            header,
                            new com.nimbusds.jose.Payload(dataToSign)
                    );
                    jwsObject.sign(signer);

                    // Extract the detached JWS signature (header..signature)
                    String[] parts = jwsObject.serialize().split("\\.");
                    String detachedJws = parts[0] + ".." + parts[2];

                    // Add proof to presentation
                    ObjectNode proof = mapper.createObjectNode();
                    proof.put("type", "JsonWebSignature2020");
                    proof.put("created", created);
                    proof.put("verificationMethod", verificationMethod);
                    proof.put("proofPurpose", "authentication");
                    proof.put("jws", detachedJws);

                    presentation.set("proof", proof);
                    return presentation;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to sign JSON-LD VP", e);
                }
            default:
                throw new UnsupportedOperationException("Unsupported presentation format: " + format);
        }
    }

    /**
     * Build the "vp" claim for a Verifiable Presentation JWT.
     * Per W3C VC Data Model, the VP should contain:
     * - @context: JSON-LD context
     * - type: VerifiablePresentation
     * - verifiableCredential: array of credential references (URIs or embedded VCs)
     *
     * Per DCP spec Section 5.4.2 and Example 5, verifiableCredential can contain:
     * - Credential IDs (URIs): ["urn:uuid:abc-123"]
     * - Full JWT VCs: ["eyJhbGc...JWT..."]
     * - JSON-LD VCs: [{...credential object...}]
     *
     * @param vp The VerifiablePresentation model
     * @return Map representing the VP claim
     */
    private Object buildVPClaim(VerifiablePresentation vp) {
        try {
            ObjectNode vpClaim = mapper.createObjectNode();
            vpClaim.putArray("@context")
                    .add("https://www.w3.org/2018/credentials/v1");
            vpClaim.putArray("type")
                    .add("VerifiablePresentation");

            // Per DCP spec: use full credentials if available, otherwise use credential IDs
            // This enables the verifier to validate VC signatures without additional fetch
            if (vp.getCredentials() != null && !vp.getCredentials().isEmpty()) {
                vpClaim.putPOJO("verifiableCredential", vp.getCredentials());
            } else {
                vpClaim.putPOJO("verifiableCredential", vp.getCredentialIds());
            }

            // Add profileId as metadata if present
            if (vp.getProfileId() != null) {
                vpClaim.put("profileId", vp.getProfileId().getSpecAlias());
            }

            return mapper.convertValue(vpClaim, Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build VP claim", e);
        }
    }
}
