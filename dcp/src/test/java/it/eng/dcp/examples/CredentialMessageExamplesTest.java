package it.eng.dcp.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.model.CredentialMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This test class generates example CredentialMessage JSON payloads for testing with Postman.
 * Run this test and copy the output from the console to use in your API requests.
 */
public class CredentialMessageExamplesTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    public void generateJwtCredentialExample() throws Exception {
        System.out.println("\n=== JWT Credential Example ===\n");

        // Generate a test JWT credential
        String jwtCredential = generateMembershipCredentialJWT();

        System.out.println("Generated JWT Token:");
        System.out.println(jwtCredential);
        System.out.println();

        // Create CredentialMessage with JWT credential
        CredentialMessage message = CredentialMessage.Builder.newInstance()
                .issuerPid("issuer-pid-12345")
                .holderPid("did:example:holder123")
                .status("ISSUED")
                .requestId("request-67890")
                .credentials(List.of(
                        CredentialMessage.CredentialContainer.Builder.newInstance()
                                .credentialType("MembershipCredential")
                                .payload(jwtCredential)
                                .format("jwt")
                                .build()
                ))
                .build();

        String json = mapper.writeValueAsString(message);
        System.out.println("Complete CredentialMessage with JWT:");
        System.out.println(json);
        System.out.println();
        System.out.println("Copy the above JSON to use in Postman POST /dcp/credentials");
        System.out.println();
    }

    @Test
    public void generateJsonLdCredentialExample() throws Exception {
        System.out.println("\n=== JSON-LD Credential Example ===\n");

        // Create a Verifiable Credential in JSON-LD format
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("@context", List.of(
                "https://www.w3.org/2018/credentials/v1",
                "https://w3id.org/security/suites/jws-2020/v1",
                "https://example.org/credentials/v1"
        ));
        credential.put("id", "http://example.org/credentials/3732");
        credential.put("type", List.of("VerifiableCredential", "OrganizationCredential"));
        credential.put("issuer", "did:example:issuer456");
        credential.put("issuanceDate", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        credential.put("expirationDate", Instant.now().plus(365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString());

        Map<String, Object> credentialSubject = new LinkedHashMap<>();
        credentialSubject.put("id", "did:example:holder123");
        credentialSubject.put("organizationName", "Example Corp");
        credentialSubject.put("role", "Member");
        credentialSubject.put("membershipLevel", "Gold");
        credential.put("credentialSubject", credentialSubject);

        // Add proof (simplified for example)
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("type", "JsonWebSignature2020");
        proof.put("created", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        proof.put("verificationMethod", "did:example:issuer456#key-1");
        proof.put("proofPurpose", "assertionMethod");
        proof.put("jws", "eyJhbGciOiJFUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..example-signature");
        credential.put("proof", proof);

        System.out.println("Verifiable Credential (JSON-LD):");
        System.out.println(mapper.writeValueAsString(credential));
        System.out.println();

        // Create CredentialMessage with JSON-LD credential
        CredentialMessage message = CredentialMessage.Builder.newInstance()
                .issuerPid("issuer-pid-12345")
                .holderPid("did:example:holder123")
                .status("ISSUED")
                .requestId("request-67890")
                .credentials(List.of(
                        CredentialMessage.CredentialContainer.Builder.newInstance()
                                .credentialType("OrganizationCredential")
                                .payload(credential)
                                .format("json-ld")
                                .build()
                ))
                .build();

        String json = mapper.writeValueAsString(message);
        System.out.println("Complete CredentialMessage with JSON-LD:");
        System.out.println(json);
        System.out.println();
        System.out.println("Copy the above JSON to use in Postman POST /dcp/credentials");
        System.out.println();
    }

    @Test
    public void generateMixedCredentialsExample() throws Exception {
        System.out.println("\n=== Mixed Credentials Example (JWT + JSON-LD) ===\n");

        // Generate JWT credential
        String jwtCredential = generateMembershipCredentialJWT();

        // Create JSON-LD credential
        Map<String, Object> jsonLdCredential = new LinkedHashMap<>();
        jsonLdCredential.put("@context", List.of(
                "https://www.w3.org/2018/credentials/v1",
                "https://example.org/credentials/v1"
        ));
        jsonLdCredential.put("id", "http://example.org/credentials/9876");
        jsonLdCredential.put("type", List.of("VerifiableCredential", "OrganizationCredential"));
        jsonLdCredential.put("issuer", "did:example:issuer456");
        jsonLdCredential.put("issuanceDate", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());

        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("id", "did:example:holder123");
        subject.put("organizationName", "Dataspace Corp");
        subject.put("registrationNumber", "REG-2024-001");
        jsonLdCredential.put("credentialSubject", subject);

        // Create CredentialMessage with both credentials
        CredentialMessage message = CredentialMessage.Builder.newInstance()
                .issuerPid("issuer-pid-12345")
                .holderPid("did:example:holder123")
                .status("ISSUED")
                .requestId("request-67890")
                .credentials(List.of(
                        CredentialMessage.CredentialContainer.Builder.newInstance()
                                .credentialType("MembershipCredential")
                                .payload(jwtCredential)
                                .format("jwt")
                                .build(),
                        CredentialMessage.CredentialContainer.Builder.newInstance()
                                .credentialType("OrganizationCredential")
                                .payload(jsonLdCredential)
                                .format("json-ld")
                                .build()
                ))
                .build();

        String json = mapper.writeValueAsString(message);
        System.out.println("Complete CredentialMessage with mixed formats:");
        System.out.println(json);
        System.out.println();
        System.out.println("Copy the above JSON to use in Postman POST /dcp/credentials");
        System.out.println();
    }

    /**
     * Note: This test is disabled because CredentialMessage validation requires at least 1 credential
     * in the credentials array, but REJECTED messages don't need credentials.
     * In the actual implementation (CredentialDeliveryService), we handle this by not validating
     * the message in the same way. This example is for documentation purposes.
     */
    @Test
    @org.junit.jupiter.api.Disabled("REJECTED messages have empty credentials which fails validation")
    public void generateRejectedCredentialExample() throws Exception {
        System.out.println("\n=== Rejected Credential Request Example ===\n");

        // Note: In practice, the rejection message is sent directly without validation
        System.out.println("Complete CredentialMessage (REJECTED):");
        System.out.println("{");
        System.out.println("  \"@context\": [\"https://w3id.org/dspace-dcp/v1.0/dcp.jsonld\"],");
        System.out.println("  \"type\": \"CredentialMessage\",");
        System.out.println("  \"issuerPid\": \"issuer-pid-12345\",");
        System.out.println("  \"holderPid\": \"did:example:holder123\",");
        System.out.println("  \"status\": \"REJECTED\",");
        System.out.println("  \"requestId\": \"request-67890\",");
        System.out.println("  \"rejectionReason\": \"Holder organization not verified\",");
        System.out.println("  \"credentials\": []");
        System.out.println("}");
        System.out.println();
        System.out.println("Copy the above JSON to use in Postman POST /dcp/credentials");
        System.out.println();
    }

    /**
     * Generates a JWT Verifiable Credential for MembershipCredential type
     */
    private String generateMembershipCredentialJWT() throws Exception {
        // Generate an EC key pair for signing (in real scenario, issuer would have a persistent key)
        ECKey ecKey = new ECKeyGenerator(Curve.P_256)
                .keyID("did:example:issuer456#key-1")
                .generate();

        // Create JWT claims for the Verifiable Credential
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer("did:example:issuer456")
                .subject("did:example:holder123")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plus(365, ChronoUnit.DAYS)))
                .jwtID("urn:uuid:credential-" + java.util.UUID.randomUUID())
                .claim("vc", Map.of(
                        "@context", List.of(
                                "https://www.w3.org/2018/credentials/v1",
                                "https://example.org/credentials/v1"
                        ),
                        "type", List.of("VerifiableCredential", "MembershipCredential"),
                        "credentialSubject", Map.of(
                                "id", "did:example:holder123",
                                "membershipId", "MEMBER-2024-001",
                                "membershipType", "Premium",
                                "status", "Active"
                        )
                ))
                .build();

        // Create and sign the JWT
        JWSSigner signer = new ECDSASigner(ecKey);
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .keyID(ecKey.getKeyID())
                        .build(),
                claimsSet
        );
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }
}

