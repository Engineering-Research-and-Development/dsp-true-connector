package it.eng.dcp.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.config.DcpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * Generates access tokens that will be included in the "token" claim of Self-Issued ID Tokens.
 * These access tokens are used by verifiers to fetch Verifiable Presentations from the
 * holder's Credential Service.
 *
 * <p>According to DCP Protocol v1.0 Section 4.3.1:
 * "The format of the token is implementation-specific and therefore should be treated
 * as an opaque string by the Verifier."
 *
 * <p>This implementation uses signed JWTs as access tokens, which allows:
 * <ul>
 *   <li>Cryptographic verification by the Credential Service</li>
 *   <li>Scope-based authorization</li>
 *   <li>Expiration control</li>
 *   <li>Replay protection via JTI</li>
 * </ul>
 */
@Service
@Slf4j
public class PresentationAccessTokenGenerator {

    private final DcpProperties props;
    private final KeyService keyService;

    @Autowired
    public PresentationAccessTokenGenerator(DcpProperties props, KeyService keyService) {
        this.props = props;
        this.keyService = keyService;
    }

    /**
     * Generates an access token that grants the verifier permission to fetch
     * Verifiable Presentations with the specified scopes.
     *
     * <p>The access token format:
     * <pre>
     * {
     *   "iss": "holder-did",           // Who issued this token
     *   "aud": "verifier-did",         // Who can use this token
     *   "scope": ["scope1", "scope2"], // What VCs/VPs can be accessed
     *   "iat": 1234567890,             // When issued
     *   "exp": 1234568190,             // When expires (5 min)
     *   "jti": "uuid",                 // Unique ID for replay protection
     *   "purpose": "presentation_query" // What this token is for
     * }
     * </pre>
     *
     * @param verifierDid The DID of the verifier who will use this token
     * @param scopes The scopes (credential types or presentation definitions) authorized
     * @return A signed JWT access token (opaque string)
     */
    public String generateAccessToken(String verifierDid, String... scopes) {
        if (verifierDid == null || verifierDid.isBlank()) {
            throw new IllegalArgumentException("verifierDid is required");
        }

        String holderDid = getHolderDid();

        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(300); // 5 minutes validity
            String jti = "access-" + UUID.randomUUID().toString();

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(holderDid)
                .subject(holderDid)
                .audience(verifierDid)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(jti)
                .claim("purpose", "presentation_query");

            // Add scopes if provided
            if (scopes != null && scopes.length > 0) {
                claimsBuilder.claim("scope", Arrays.asList(scopes));
                log.debug("Access token scopes: {}", Arrays.toString(scopes));
            } else {
                // No scopes means access to all presentations (use with caution)
                log.warn("Generating access token with no scope restrictions for verifier: {}", verifierDid);
            }

            JWTClaimsSet claims = claimsBuilder.build();

            // Sign the access token
            ECKey signingJwk = keyService.getSigningJwk();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(signingJwk.getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            JWSSigner signer = new ECDSASigner(signingJwk.toECPrivateKey());
            jwt.sign(signer);

            String accessToken = jwt.serialize();

            log.debug("Generated access token for verifier: {} with {} scopes",
                      verifierDid, scopes != null ? scopes.length : 0);

            return accessToken;

        } catch (JOSEException e) {
            log.error("Failed to generate access token for verifier: {}", verifierDid, e);
            throw new RuntimeException("Failed to generate access token", e);
        }
    }

    /**
     * Generates a simple opaque access token (non-JWT) that can be validated
     * via a token store or cache.
     *
     * <p>This is a lighter-weight alternative to JWT access tokens.
     * The token is a random UUID that must be stored and validated by the
     * Credential Service.
     *
     * @param verifierDid The verifier who will use this token
     * @param scopes The scopes authorized
     * @return An opaque token string (UUID-based)
     */
    public String generateOpaqueToken(String verifierDid, String... scopes) {
        if (verifierDid == null || verifierDid.isBlank()) {
            throw new IllegalArgumentException("verifierDid is required");
        }

        // Generate a random opaque token
        String opaqueToken = UUID.randomUUID().toString();

        log.debug("Generated opaque access token: {} for verifier: {}", opaqueToken, verifierDid);

        // Note: The token should be stored with its metadata (verifier, scopes, expiration)
        // in a token store for later validation by the Credential Service.
        // This is implementation-specific and not shown here.

        return opaqueToken;
    }

    /**
     * Generates a presentation ID that can be used as the token claim.
     * This is a reference to a pre-created presentation that the verifier can fetch.
     *
     * @return A unique presentation ID
     */
    public String generatePresentationId() {
        String presentationId = "presentation-" + UUID.randomUUID().toString();
        log.debug("Generated presentation ID: {}", presentationId);
        return presentationId;
    }

    /**
     * Gets the holder's DID from configuration.
     *
     * @return The holder's DID
     */
    private String getHolderDid() {
        String holderDid = props.getConnectorDid();

        if (holderDid == null || holderDid.isBlank()) {
            throw new IllegalStateException("Holder DID not configured. Please set dcp.connector.did property");
        }

        return holderDid;
    }
}

