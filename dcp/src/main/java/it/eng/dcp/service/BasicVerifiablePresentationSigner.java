package it.eng.dcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.eng.dcp.model.VerifiablePresentation;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.UUID;

@Service
public class BasicVerifiablePresentationSigner implements VerifiablePresentationSigner {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Object sign(VerifiablePresentation vp, String format) {
        if (format == null) format = "json-ld";
        switch (format.toLowerCase()) {
            case "jwt":
                // produce a very small compact representation for tests: header.payload.signature (base64)
                try {
                    ObjectNode header = mapper.createObjectNode();
                    header.put("alg", "none");
                    header.put("typ", "JWT");

                    ObjectNode payload = mapper.createObjectNode();
                    payload.put("vpId", vp.getId());
                    payload.putPOJO("credentialIds", vp.getCredentialIds());
                    payload.put("profileId", vp.getProfileId());

                    String compact = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(header))
                            + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(payload))
                            + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes());
                    return compact;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create JWT-style VP", e);
                }
            case "json-ld":
                ObjectNode presentation = mapper.createObjectNode();
                presentation.put("id", vp.getId());
                presentation.putPOJO("credentialIds", vp.getCredentialIds());
                presentation.put("profileId", vp.getProfileId());

                ObjectNode proof = mapper.createObjectNode();
                proof.put("type", "Ed25519Signature2020-placeholder");
                proof.put("created", java.time.Instant.now().toString());
                proof.put("proofPurpose", "assertionMethod");
                proof.put("verificationMethod", "urn:placeholder:verificationMethod");

                presentation.set("proof", proof);
                return presentation;
            default:
                throw new UnsupportedOperationException("Unsupported presentation format: " + format);
        }
    }
}

