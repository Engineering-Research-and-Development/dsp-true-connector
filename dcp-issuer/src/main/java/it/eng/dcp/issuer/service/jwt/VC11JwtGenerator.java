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
 * VC 1.1 JWT Generator - implements vc11-sl2021/jwt profile.
 *
 * <p>Generates Verifiable Credentials following the W3C VC Data Model 1.1 specification
 * with StatusList2021 and external proofs using JWT.
 *
 * <p>Key features:
 * <ul>
 *   <li>Uses /2018/credentials/v1 context</li>
 *   <li>Uses issuanceDate/expirationDate</li>
 *   <li>External proof (proof object inside vc claim)</li>
 *   <li>StatusList2021Entry for credential status (placeholder)</li>
 *   <li>JWT header type: standard "JWT"</li>
 *   <li>Nested structure (credential data in vc claim)</li>
 * </ul>
 */
@Slf4j
public class VC11JwtGenerator implements VcJwtGenerator {

    private final String issuerDid;
    private final KeyService keyService;
    private final BaseDidDocumentConfiguration didDocumentConfig;

    /**
     * Create a new VC 1.1 JWT generator.
     *
     * @param issuerDid The issuer's DID
     * @param keyService Service for retrieving signing keys
     * @param didDocumentConfig DID document configuration
     */
    public VC11JwtGenerator(String issuerDid, KeyService keyService, BaseDidDocumentConfiguration didDocumentConfig) {
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

            // Build VC 1.1 structure with external proof
            Map<String, Object> vc = new HashMap<>();
            vc.put("@context", List.of(
                    "https://www.w3.org/2018/credentials/v1",
                    "https://example.org/credentials/v1"
            ));
            vc.put("id", "urn:uuid:" + UUID.randomUUID());
            vc.put("type", List.of("VerifiableCredential", credentialType));
            vc.put("issuer", issuerDid);
            vc.put("issuanceDate", now.toString());
            vc.put("expirationDate", expiration.toString());

            Map<String, Object> credentialSubject = new HashMap<>(claims);
            credentialSubject.put("id", holderDid);
            vc.put("credentialSubject", credentialSubject);

            // Add StatusList2021Entry if info is provided
            if (statusListId != null && statusListIndex != null) {
                Map<String, Object> credentialStatus = new HashMap<>();
                credentialStatus.put("id", statusListId + "#" + statusListIndex);
                credentialStatus.put("type", "StatusList2021Entry");
                credentialStatus.put("statusPurpose", "revocation");
                credentialStatus.put("statusListIndex", String.valueOf(statusListIndex));
                credentialStatus.put("statusListCredential", statusListId);
                vc.put("credentialStatus", credentialStatus);
            }

            // Add external proof object (placeholder - actual proof is JWT signature)
            Map<String, Object> proof = new HashMap<>();
            proof.put("type", "JsonWebSignature2020");
            proof.put("created", now.toString());
            proof.put("verificationMethod", issuerDid + "#" + signingKey.getKeyID());
            proof.put("proofPurpose", "assertionMethod");
            vc.put("proof", proof);

            // Build JWT claims with nested vc claim (VC 1.1 pattern)
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .issuer(issuerDid)
                    .subject(holderDid)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiration))
                    .jwtID("urn:uuid:" + UUID.randomUUID())
                    .claim("vc", vc)
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(signingKey.getKeyID())
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claimsSet);
            JWSSigner signer = new ECDSASigner(signingKey);
            signedJWT.sign(signer);

            log.debug("Generated VC 1.1 credential (vc11-sl2021/jwt) for type: {}", credentialType);
            return signedJWT.serialize();

        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign VC 1.1 JWT", e);
        }
    }
}
