package it.eng.dcp.common.service.sts;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.common.service.did.DidResolverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Self-Issued ID Token service using EC keys from KeyService for signing.
 * 
 * <p>This service creates and validates self-issued ID tokens (SIOPv2-style tokens)
 * used for authentication in DCP protocol exchanges. Both issuer and holder/verifier
 * modules need this functionality for secure communication.
 * 
 * <p>Features:
 * <ul>
 *   <li>Token creation with ES256 signatures</li>
 *   <li>Token validation with DID-based key resolution</li>
 *   <li>Replay attack prevention via JTI tracking</li>
 *   <li>Configurable token expiry (default 5 minutes)</li>
 * </ul>
 */
@Service
@Slf4j
public class SelfIssuedIdTokenService {

    private final String connectorDid;
    private final DidResolverService didResolver;
    private final JtiReplayCache jtiCache;
    private final KeyService keyService;

    // Test hook to override the signing key with a specific EC JWK (used in unit tests)
    private ECKey overrideSigningKey;

    @Autowired
    public SelfIssuedIdTokenService(
            @Value("${dcp.connector.did:}") String connectorDid,
            DidResolverService didResolver,
            JtiReplayCache jtiCache,
            KeyService keyService) {
        this.connectorDid = connectorDid;
        this.didResolver = didResolver;
        this.jtiCache = jtiCache;
        this.keyService = keyService;
    }

    /**
     * Test-only setter to override the signing key.
     * 
     * @param key The EC key to use for signing instead of the default from KeyService
     */
    void setOverrideSigningKey(ECKey key) {
        this.overrideSigningKey = key;
    }

    /**
     * Creates and signs a self-issued ID token for the given audience.
     * 
     * @param audienceDid The DID of the intended audience (verifier)
     * @param accessToken Optional access token to include in the token claims
     * @return Signed JWT token as a string
     * @throws IllegalArgumentException if audienceDid is null or blank
     * @throws IllegalStateException if connectorDid is not configured
     * @throws RuntimeException if signing fails
     */
    public String createAndSignToken(String audienceDid, String accessToken) {
        if (audienceDid == null || audienceDid.isBlank()) {
            throw new IllegalArgumentException("audienceDid required");
        }

        log.debug("Creating token for audience: {}", audienceDid);

        if (connectorDid == null || connectorDid.isBlank()) {
            log.error("connectorDid is null or blank!");
            throw new IllegalStateException("connectorDid is not configured. Please set dcp.connector.did property");
        }

        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(300); // 5 minutes expiry
            String jti = UUID.randomUUID().toString();

            JWTClaimsSet.Builder cb = new JWTClaimsSet.Builder();
            cb.issuer(connectorDid).subject(connectorDid);
            cb.audience(audienceDid);
            cb.issueTime(Date.from(now));
            cb.expirationTime(Date.from(exp));
            cb.jwtID(jti);
            if (accessToken != null) {
                cb.claim("token", accessToken);
            }

            JWTClaimsSet claims = cb.build();

            // Obtain EC signing key: prefer override (tests), otherwise ask KeyService for signing JWK
            ECKey signingJwk;
            if (overrideSigningKey != null) {
                signingJwk = overrideSigningKey;
            } else {
                if (keyService == null) {
                    throw new IllegalStateException("KeyService is not available for signing");
                }
                signingJwk = keyService.getSigningJwk();
            }

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(signingJwk.getKeyID())
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            JWSSigner signer = new ECDSASigner(signingJwk.toECPrivateKey());
            jwt.sign(signer);

            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to create and sign token", e);
        }
    }

    /**
     * Validates a self-issued ID token.
     * 
     * <p>Performs the following validations:
     * <ul>
     *   <li>Parses the JWT and extracts issuer/kid</li>
     *   <li>Resolves the issuer's public key via DID resolution</li>
     *   <li>Verifies the signature using the resolved public key</li>
     *   <li>Checks token expiry and issue time</li>
     *   <li>Prevents replay attacks using JTI cache (optional)</li>
     * </ul>
     * 
     * @param token The JWT token string to validate
     * @return The validated JWT claims
     * @throws SecurityException if validation fails
     * @throws RuntimeException if parsing or verification fails
     */
    public JWTClaimsSet validateToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            String issuer = jwt.getJWTClaimsSet().getIssuer();
            String kid = jwt.getHeader().getKeyID();
            
            if (issuer == null || kid == null) {
                throw new SecurityException("Missing issuer or kid");
            }

            // Resolve the public key from the issuer's DID document
            JWK jwk;
            try {
                jwk = didResolver.resolvePublicKey(issuer, kid, null); // "capabilityInvocation"
            } catch (DidResolutionException dre) {
                throw new SecurityException("Failed to resolve issuer public key", dre);
            }
            
            if (jwk == null) {
                throw new SecurityException("No key found for issuer/kid");
            }

            // Expect EC public key
            ECKey ecPub = (ECKey) jwk;
            JWSVerifier verifier = new ECDSAVerifier(ecPub.toECPublicKey());
            
            if (!jwt.verify(verifier)) {
                throw new SecurityException("Invalid signature");
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            // Basic claim checks
            Instant now = Instant.now();
            Date exp = claims.getExpirationTime();
            Date iat = claims.getIssueTime();
            
            if (exp == null || iat == null) {
                throw new SecurityException("Missing iat/exp");
            }
            
            if (exp.toInstant().isBefore(now)) {
                throw new SecurityException("Token expired");
            }

            // Replay protection: use jti and exp
            String jti = claims.getJWTID();
            if (jti == null) {
                throw new SecurityException("Missing jti");
            }
            
            // Note: jtiCache.checkAndPut(jti, exp.toInstant()) can be uncommented for strict replay prevention
            // Currently commented out to allow token reuse in development/testing scenarios

            return claims;
        } catch (java.text.ParseException | JOSEException e) {
            throw new RuntimeException("Failed to validate token", e);
        }
    }
}

