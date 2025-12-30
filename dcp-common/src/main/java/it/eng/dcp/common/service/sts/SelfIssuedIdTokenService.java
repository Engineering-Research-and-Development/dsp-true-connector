package it.eng.dcp.common.service.sts;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.common.util.DidUrlConverter;
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

//    private final String connectorDid;
    private final DidResolverService didResolver;
    private final JtiReplayCache jtiCache;
    private final KeyService keyService;
    private final BaseDidDocumentConfiguration config;

    // Test hook to override the signing key with a specific EC JWK (used in unit tests)
    private ECKey overrideSigningKey;

    @Autowired
    public SelfIssuedIdTokenService(
//            @Value("${dcp.connector.did:}") String connectorDid,
            DidResolverService didResolver,
            JtiReplayCache jtiCache,
            KeyService keyService,
            BaseDidDocumentConfiguration config) {
//        this.connectorDid = connectorDid;
        this.didResolver = didResolver;
        this.jtiCache = jtiCache;
        this.keyService = keyService;
        this.config = config;
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
     * @param config DidDocumentConfig containing keystore details for signing
     * @return Signed JWT token as a string
     * @throws IllegalArgumentException if audienceDid is null or blank
     * @throws IllegalStateException if connectorDid is not configured
     * @throws RuntimeException if signing fails
     */
    public String createAndSignToken(String audienceDid, String accessToken, DidDocumentConfig config) {
        if (audienceDid == null || audienceDid.isBlank()) {
            throw new IllegalArgumentException("audienceDid required");
        }

        log.debug("Creating token for audience: {}", audienceDid);

        if (config.getDid() == null || config.getDid().isBlank()) {
            log.error("connectorDid is null or blank!");
            throw new IllegalStateException("connectorDid is not configured. Please set dcp.connector.did property");
        }

        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(300); // 5 minutes expiry
            String jti = UUID.randomUUID().toString();

            JWTClaimsSet.Builder cb = new JWTClaimsSet.Builder();
            cb.issuer(config.getDid()).subject(config.getDid());
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
                signingJwk = keyService.getSigningJwk(config);
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
     *   <li>Checks token expiry, not-before, and issue time</li>
     *   <li>Checks iss == sub, aud == verifierDid, sub == DID Document id</li>
     *   <li>Checks capabilityInvocation relationship</li>
     *   <li>Handles kid header logic</li>
     *   <li>Prevents replay attacks using JTI cache</li>
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
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            String issuer = claims.getIssuer();
            String subject = claims.getSubject();
            String aud = claims.getAudience() != null && !claims.getAudience().isEmpty() ? claims.getAudience().get(0) : null;
            String kid = jwt.getHeader().getKeyID();

            // 1. iss and sub must be the same (using robust DID comparison)
            if (issuer == null || subject == null || !DidUrlConverter.compareDids(issuer, subject)) {
                throw new SecurityException("iss and sub must be present and equal");
            }

            String connectorDid = config.getDidDocumentConfig().getDid();
            // 2. aud must match verifierDid (using robust DID comparison)
            if (aud == null || connectorDid == null || !DidUrlConverter.compareDids(aud, connectorDid)) {
                throw new SecurityException("aud claim must match verifier DID");
            }

            // 3. Resolve the public key for sub DID and kid with capabilityInvocation
            JWK jwk = didResolver.resolvePublicKey(subject, kid, "capabilityInvocation");
            if (jwk == null) {
                throw new SecurityException("No JWK found for sub DID with capabilityInvocation");
            }

            // 4. Verify signature
            ECKey ecPub = (ECKey) jwk;
            JWSVerifier verifier = new ECDSAVerifier(ecPub.toECPublicKey());
            if (!jwt.verify(verifier)) {
                throw new SecurityException("Invalid signature");
            }

            // 5. Check nbf if present (allow 2 min clock skew)
            Instant now = Instant.now();
            Date nbf = claims.getNotBeforeTime();
            if (nbf != null && now.isBefore(nbf.toInstant().minusSeconds(120))) {
                throw new SecurityException("Token not valid yet (nbf)");
            }

            // 6. Check exp (allow 2 min clock skew)
            Date exp = claims.getExpirationTime();
            if (exp == null || now.isAfter(exp.toInstant().plusSeconds(120))) {
                throw new SecurityException("Token expired");
            }

            // 7. Optionally, check iat (reject if too old, e.g., >1h)
            Date iat = claims.getIssueTime();
            if (iat == null) {
                throw new SecurityException("Missing iat");
            }
            if (now.isAfter(iat.toInstant().plusSeconds(3600))) {
                throw new SecurityException("Token issued too far in the past");
            }
            // Reject if iat is in the future (allow 2 min clock skew)
            if (now.isBefore(iat.toInstant().minusSeconds(120))) {
                throw new SecurityException("Token issued in the future (iat)");
            }

            // 8. jti must be present and not used before
            String jti = claims.getJWTID();
            if (jti == null) {
                throw new SecurityException("Missing jti");
            }
            try {
                jtiCache.checkAndPut(jti, exp.toInstant());
            } catch (IllegalStateException e) {
                throw new SecurityException("jti has already been used", e);
            }

            log.debug("Token validation successful for subject: {}", subject);
            return claims;
        } catch (SecurityException e) {
            log.warn("Token validation failed: {}", e.getMessage());
            throw e;
        } catch (DidResolutionException e) {
            log.warn("Token validation failed - DID resolution error: {}", e.getMessage());
            throw new SecurityException("Failed to resolve DID: " + e.getMessage(), e);
        } catch (java.text.ParseException | JOSEException e) {
            log.error("Token validation failed - parse/crypto error: {}", e.getMessage());
            throw new RuntimeException("Failed to validate token", e);
        }
    }
}
