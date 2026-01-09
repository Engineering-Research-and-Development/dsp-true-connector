package it.eng.dcp.issuer.service.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.service.KeyService;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;

/**
 * VC 2.0 JWT Generator - implements vc20-bssl/jwt profile.
 *
 * <p>Generates Verifiable Credentials following the W3C VC Data Model 2.0 specification
 * with BitstringStatusList and enveloped proofs using JWT/JOSE.
 *
 * <p>Key features:
 * <ul>
 *   <li>Uses /ns/credentials/v2 context</li>
 *   <li>Uses validFrom/validUntil instead of issuanceDate/expirationDate</li>
 *   <li>Enveloped proof (no proof object in payload, JWT signature IS the proof)</li>
 *   <li>BitstringStatusListEntry for credential status</li>
 *   <li>JWT header type: "vc+ld+jwt"</li>
 *   <li>Flat structure (all fields at root level, no nested vc claim)</li>
 * </ul>
 */
@Slf4j
public class VC20JwtGenerator implements VcJwtGenerator {

    private final String issuerDid;
    private final KeyService keyService;
    private final BaseDidDocumentConfiguration didDocumentConfig;

    /**
     * Create a new VC 2.0 JWT generator.
     *
     * @param issuerDid The issuer's DID
     * @param keyService Service for retrieving signing keys
     * @param didDocumentConfig DID document configuration
     */
    public VC20JwtGenerator(String issuerDid, KeyService keyService, BaseDidDocumentConfiguration didDocumentConfig) {
        this.issuerDid = issuerDid;
        this.keyService = keyService;
        this.didDocumentConfig = didDocumentConfig;
    }

    @Override
    public String generateJwt(String holderDid, String credentialType, Map<String, String> claims) {
        return generateJwt(holderDid, credentialType, claims, null, null);
    }

    public String generateJwt(String holderDid, String credentialType, Map<String, String> claims, String statusListId, Integer statusListIndex) {
        try {
            ECKey signingKey = keyService.getSigningJwk(didDocumentConfig.getDidDocumentConfig());

            Instant now = Instant.now();
            Instant expiration = now.plusSeconds(365 * 24 * 60 * 60); // 1 year

            // Build credentialSubject
            Map<String, Object> credentialSubject = new HashMap<>(claims);
            credentialSubject.put("id", holderDid);

            // Build credentialStatus (BitstringStatusListEntry)
            Map<String, Object> credentialStatus = null;
            if (statusListId != null && statusListIndex != null) {
                credentialStatus = new HashMap<>();
                credentialStatus.put("id", statusListId + "#" + statusListIndex);
                credentialStatus.put("type", "BitstringStatusListEntry");
                credentialStatus.put("statusPurpose", "revocation");
                credentialStatus.put("statusListIndex", String.valueOf(statusListIndex));
                credentialStatus.put("statusListCredential", statusListId);
            }

            // Build issuer object (VC 2.0 allows issuer as object)
            Map<String, Object> issuer = new HashMap<>();
            issuer.put("id", issuerDid);

            // Build JWT claims with VC 2.0 structure (flat, not nested in "vc" claim)
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .issuer(issuerDid)
                    .subject(holderDid)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiration))
                    .jwtID("urn:uuid:" + UUID.randomUUID());

            // Add VC 2.0 specific claims at root level
            claimsBuilder.claim("@context", List.of(
                    "https://www.w3.org/ns/credentials/v2",
                    "https://www.w3.org/ns/credentials/examples/v2"
            ));
            claimsBuilder.claim("type", List.of("VerifiableCredential", credentialType));
            claimsBuilder.claim("issuer", issuer);
            claimsBuilder.claim("validFrom", now.toString());
            claimsBuilder.claim("validUntil", expiration.toString());
            claimsBuilder.claim("credentialSubject", credentialSubject);
            if (credentialStatus != null) {
                claimsBuilder.claim("credentialStatus", credentialStatus);
            }

            JWTClaimsSet jwtClaims = claimsBuilder.build();

            // Create JWT header with VC 2.0 media type
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(signingKey.getKeyID())
                    .type(new com.nimbusds.jose.JOSEObjectType("vc+ld+jwt"))  // VC 2.0 media type
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, jwtClaims);
            JWSSigner signer = new ECDSASigner(signingKey);
            signedJWT.sign(signer);

            log.debug("Generated VC 2.0 credential (vc20-bssl/jwt) for type: {}", credentialType);
            return signedJWT.serialize();

        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign VC 2.0 JWT", e);
        }
    }
}
