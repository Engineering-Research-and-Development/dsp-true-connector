package it.eng.dcp.common.service.sts;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.common.service.did.DidResolverService;
import it.eng.dcp.common.util.DidUrlConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
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

    /** Default clock skew in seconds, used when {@code dcp.clock-skew-seconds} is not set. */
    static final long DEFAULT_CLOCK_SKEW_SECONDS = 120;

//    private final String connectorDid;
    private final DidResolverService didResolver;
    private final JtiReplayCache jtiCache;
    private final KeyService keyService;
    private final DidDocumentConfig config;
    private final DcpAuditEventPublisher auditPublisher;

    /**
     * Clock skew in seconds allowed on {@code nbf}, {@code exp} and {@code iat}; a used {@code jti} is
     * remembered until {@code exp} plus this skew. Property {@code dcp.clock-skew-seconds}, default 120.
     */
    private long clockSkewSeconds = DEFAULT_CLOCK_SKEW_SECONDS;

    // Test hook to override the signing key with a specific EC JWK (used in unit tests)
    private ECKey overrideSigningKey;

    @Autowired
    public SelfIssuedIdTokenService(
//            @Value("${dcp.connector.did:}") String connectorDid,
            DidResolverService didResolver,
            JtiReplayCache jtiCache,
            KeyService keyService,
            DidDocumentConfig config,
            DcpAuditEventPublisher auditPublisher) {
//        this.connectorDid = connectorDid;
        this.didResolver = didResolver;
        this.jtiCache = jtiCache;
        this.keyService = keyService;
        this.config = config;
        this.auditPublisher = auditPublisher;
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

            String serialized = jwt.serialize();
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_ISSUED,
                    "Self-issued ID token issued for audience: " + audienceDid,
                    "common",
                    null, null, null, null,
                    Map.of("audience", audienceDid));
            return serialized;
        } catch (JOSEException e) {
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Failed to create and sign token for audience: " + audienceDid + ": " + e.getMessage(),
                    "common",
                    null, null, null, null,
                    Map.of("audience", audienceDid, "error", e.getMessage()));
            throw new RuntimeException("Failed to create and sign token", e);
        }
    }

    /**
     * Validates a self-issued ID token.
     * 
     * <p>Performs the validations of DCP §4.3.3:
     * <ul>
     *   <li>Parses the JWT and extracts issuer/kid</li>
     *   <li>Checks iss == sub and aud == this participant's DID</li>
     *   <li>Resolves the {@code sub} DID document and checks sub == DID Document id</li>
     *   <li>Selects the key per the kid rules (kid match; no kid: the single verification method)
     *       and requires the {@code capabilityInvocation} relationship</li>
     *   <li>Verifies the signature with the selected key (EC keys; other key types are rejected)</li>
     *   <li>Checks token expiry, not-before, and issue time</li>
     *   <li>Prevents replay attacks using JTI tracking</li>
     * </ul>
     * 
     * @param token The JWT token string to validate
     * @return The validated JWT claims
     * @throws SecurityException if validation or parsing fails
     */
    public JWTClaimsSet validateToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            String issuer = claims.getIssuer();
            String subject = claims.getSubject();
            String aud = claims.getAudience() != null && !claims.getAudience().isEmpty() ? claims.getAudience().get(0) : null;
            String kid = jwt.getHeader().getKeyID();

            log.debug("Validating Self-Issued ID Token: iss={}, kid={}", issuer, kid);

            // 1. iss and sub must be the same (using robust DID comparison)
            if (issuer == null || subject == null || !DidUrlConverter.compareDids(issuer, subject)) {
                throw new SecurityException("iss and sub must be present and equal");
            }

            String connectorDid = config.getDid();
            // 2. aud must match verifierDid (using robust DID comparison)
            if (aud == null || connectorDid == null || !DidUrlConverter.compareDids(aud, connectorDid)) {
                throw new SecurityException("aud claim must match verifier DID");
            }

            // 3. sub must equal the id of the resolved DID document (§4.3.3 step 4)
            DidDocument didDocument;
            try {
                didDocument = didResolver.fetchDidDocumentCached(subject);
            } catch (IOException e) {
                throw new DidResolutionException("Failed to fetch DID document for sub", e);
            } catch (IllegalArgumentException e) {
                // e.g. a sub that is not a did:web DID
                throw new DidResolutionException("Unsupported or invalid sub DID: " + e.getMessage(), e);
            }
            if (didDocument == null || didDocument.getId() == null
                    || !DidUrlConverter.compareDids(subject, didDocument.getId())) {
                throw new SecurityException("sub must equal the resolved DID Document id");
            }

            // 4. Resolve the public key for sub DID and kid with capabilityInvocation
            JWK jwk = didResolver.resolvePublicKey(subject, kid, DidDocument.CAPABILITY_INVOCATION);
            if (jwk == null) {
                throw new SecurityException("No JWK found for sub DID with capabilityInvocation");
            }

            // 5. Verify signature
            if (!jwt.verify(createVerifier(jwk))) {
                throw new SecurityException("Invalid signature");
            }

            // 6. Check nbf if present (allow clock skew)
            Instant now = Instant.now();
            Date nbf = claims.getNotBeforeTime();
            if (nbf != null && now.isBefore(nbf.toInstant().minusSeconds(clockSkewSeconds))) {
                throw new SecurityException("Token not valid yet (nbf)");
            }

            // 7. Check exp (allow clock skew)
            Date exp = claims.getExpirationTime();
            if (exp == null || now.isAfter(exp.toInstant().plusSeconds(clockSkewSeconds))) {
                throw new SecurityException("Token expired");
            }

            // 8. Optionally, check iat (reject if too old, e.g., >1h)
            Date iat = claims.getIssueTime();
            if (iat == null) {
                throw new SecurityException("Missing iat");
            }
            if (now.isAfter(iat.toInstant().plusSeconds(3600))) {
                throw new SecurityException("Token issued too far in the past");
            }
            // Reject if iat is in the future (allow clock skew)
            if (now.isBefore(iat.toInstant().minusSeconds(clockSkewSeconds))) {
                throw new SecurityException("Token issued in the future (iat)");
            }

            // 9. jti must be present and not used before
            String jti = claims.getJWTID();
            if (jti == null) {
                throw new SecurityException("Missing jti");
            }
            try {
                // Keep the jti for as long as the token is accepted (exp + skew), so it cannot be
                // replayed inside the clock-skew window after exp
                jtiCache.checkAndPut(jti, exp.toInstant().plusSeconds(clockSkewSeconds));
            } catch (IllegalStateException e) {
                throw new SecurityException("jti has already been used", e);
            }

            log.debug("Token validation successful for subject: {}", subject);
            auditPublisher.publishEvent(
                    DcpAuditEventType.SELF_ISSUED_TOKEN_VALIDATED,
                    "Self-issued ID token validated for subject: " + subject,
                    "common",
                    null, null, null, null,
                    Map.of("subject", subject, "audience", aud != null ? aud : ""));
            return claims;
        } catch (SecurityException e) {
            log.warn("Token validation failed: {}", e.getMessage());
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Token validation failed: " + e.getMessage(),
                    "common",
                    null, null, null, null,
                    Map.of("reason", e.getMessage()));
            throw e;
        } catch (DidResolutionException e) {
            log.warn("Token validation failed - DID resolution error: {}", e.getMessage());
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Token validation failed - DID resolution error: " + e.getMessage(),
                    "common",
                    null, null, null, null,
                    Map.of("reason", e.getMessage()));
            throw new SecurityException("Failed to resolve DID: " + e.getMessage(), e);
        } catch (JOSEException e) {
            log.warn("Token validation failed - signature verification error: {}", e.getMessage());
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Token validation failed - signature verification error: " + e.getMessage(),
                    "common",
                    null, null, null, null,
                    Map.of("reason", e.getMessage()));
            throw new SecurityException("Invalid signature", e);
        } catch (ParseException e) {
            log.error("Token validation failed - parse/crypto error: {}", e.getMessage());
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Token validation failed - invalid token format: " + e.getMessage(),
                    "common",
                    null, null, null, null,
                    Map.of("reason", e.getMessage()));
            throw new SecurityException("Invalid token format", e);
        }
    }

    /**
     * Creates a signature verifier for the resolved key.
     *
     * @param jwk the public key selected from the DID document
     * @return a verifier for the key
     * @throws SecurityException if the key type is not supported
     * @throws JOSEException if the key cannot be converted
     */
    private JWSVerifier createVerifier(JWK jwk) throws JOSEException {
        if (jwk instanceof ECKey ecKey) {
            return new ECDSAVerifier(ecKey.toECPublicKey());
        }
        throw new SecurityException("Unsupported key type: " + jwk.getKeyType());
    }

    /**
     * Creates a wrapper JWT token that contains a nested Access Token in the "token" claim.
     * This format is used by STS-compatible endpoints where the access_token field contains a JWT
     * with a nested token claim containing scope-based authorization.
     *
     * <p>Per DCP Protocol v1.0 Section 4.3.1 and 5.1:
     * <ul>
     *   <li>Outer JWT: Self-Issued ID Token (iss==sub, contains "token" claim)</li>
     *   <li>Inner JWT: Access Token (contains "scope" claim for authorization)</li>
     * </ul>
     *
     * @param audienceDid The DID of the intended audience (verifier)
     * @param config DidDocumentConfig containing keystore details for signing
     * @param scopes The credential types or scopes the verifier is authorized to access
     * @return Signed JWT token with nested access token containing scopes
     * @throws IllegalArgumentException if audienceDid is null or blank
     * @throws IllegalStateException if connectorDid is not configured
     * @throws RuntimeException if signing fails
     */
    public String createStsCompatibleToken(String audienceDid, DidDocumentConfig config, String... scopes) {
        if (audienceDid == null || audienceDid.isBlank()) {
            throw new IllegalArgumentException("audienceDid required");
        }

        log.info("Creating STS-compatible wrapper token for audience: {} with {} scopes",
                 audienceDid, scopes != null ? scopes.length : 0);

        if (config.getDid() == null || config.getDid().isBlank()) {
            log.error("connectorDid is null or blank!");
            throw new IllegalStateException("connectorDid is not configured. Please set dcp.connector.did property");
        }

        try {
            // First, create the inner Access Token with scopes
            // Generate self issued access token with scope claim - holder for holder
            String innerToken = createAccessTokenWithScopes(config.getDid(), config, scopes);

            // Now create a wrapper Self-Issued ID Token that contains the inner token in the "token" claim
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(300); // 5 minutes expiry
            String jti = "urn:uuid:" + UUID.randomUUID();

            JWTClaimsSet.Builder cb = new JWTClaimsSet.Builder();
            cb.issuer(config.getDid()).subject(config.getDid()); // Self-issued: iss == sub
            cb.audience(audienceDid);
            cb.issueTime(Date.from(now));
            cb.expirationTime(Date.from(exp));
            cb.jwtID(jti);
            cb.claim("token", innerToken); // Nested access token claim

            JWTClaimsSet claims = cb.build();

            // Obtain EC signing key
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

            String serialized = jwt.serialize();
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_ISSUED,
                    "Self-issued ID token issued for audience: " + audienceDid,
                    "common",
                    null, null, null, null,
                    Map.of("audience", audienceDid));
            return serialized;
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to create and sign STS-compatible token", e);
        }
    }

    /**
     * Creates an Access Token with scope-based authorization for presentation queries.
     * This token is embedded in the Self-Issued ID Token's "token" claim.
     *
     * <p>Per DCP Protocol v1.0 Section 5.1, the access token MUST include:
     * <ul>
     *   <li>iss: Holder's DID (who issued the token)</li>
     *   <li>sub: Holder's DID (subject of the token)</li>
     *   <li>aud: Verifier's DID (who can use the token)</li>
     *   <li>scope: Credential types the verifier can access (CRITICAL for security)</li>
     *   <li>Standard claims: iat, exp, nbf, jti</li>
     * </ul>
     *
     * @param verifierDid The DID of the verifier who will use this token
     * @param config DidDocumentConfig containing holder's DID and keystore
     * @param scopes The credential types authorized for access
     * @return Signed JWT access token with scope claim
     * @throws JOSEException if signing fails
     */
    private String createAccessTokenWithScopes(String verifierDid, DidDocumentConfig config, String... scopes)
            throws JOSEException {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(300); // 5 minutes validity
        String jti = "urn:uuid:" + UUID.randomUUID();

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
            .issuer(config.getDid())
            .subject(config.getDid()) // Per spec: holder is both issuer and subject of access token
            .audience(verifierDid)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(exp))
            .notBeforeTime(Date.from(now)) // Valid immediately
            .jwtID(jti);

        // Add scopes if provided (CRITICAL for security)
        if (scopes != null && scopes.length > 0) {
            claimsBuilder.claim("scope", Arrays.asList(scopes));
            log.debug("Access token scopes: {}", Arrays.toString(scopes));
        } else {
            // No scopes means access to all presentations (use with caution)
            log.warn("Generating access token with NO scope restrictions for verifier: {}. " +
                     "This grants access to ALL credentials!", verifierDid);
        }

        JWTClaimsSet claims = claimsBuilder.build();

        // Sign the access token
        ECKey signingJwk;
        if (overrideSigningKey != null) {
            signingJwk = overrideSigningKey;
        } else {
            signingJwk = keyService.getSigningJwk(config);
        }

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signingJwk.getKeyID())
                .type(JOSEObjectType.JWT)
                .build();

        try {
            SignedJWT jwt = new SignedJWT(header, claims);
            JWSSigner signer = new ECDSASigner(signingJwk.toECPrivateKey());
            jwt.sign(signer);
            String serialized = jwt.serialize();
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_ISSUED,
                    "Access token issued for verifier: " + verifierDid,
                    "common",
                    null, null,
                    scopes != null ? Arrays.asList(scopes) : null,
                    null,
                    Map.of("verifier", verifierDid));
            return serialized;
        } catch (JOSEException e) {
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Failed to sign access token for verifier: " + verifierDid + ": " + e.getMessage(),
                    "common",
                    null, null, null, null,
                    Map.of("verifier", verifierDid, "error", e.getMessage()));
            throw e;
        }
    }

    /**
     * Backward-compatible overload without scopes.
     *
     * @param audienceDid The DID of the intended audience (verifier)
     * @param config DidDocumentConfig containing keystore details for signing
     * @return Signed JWT token with nested token claim (without scope restrictions)
     * @deprecated Use {@link #createStsCompatibleToken(String, DidDocumentConfig, String...)} with explicit scopes
     */
    @Deprecated
    public String createStsCompatibleToken(String audienceDid, DidDocumentConfig config) {
        log.warn("Creating STS-compatible token WITHOUT scopes - consider specifying scopes for security");
        return createStsCompatibleToken(audienceDid, config, new String[0]);
    }

    /**
     * Sets the clock skew from {@code dcp.clock-skew-seconds}.
     *
     * @param clockSkewSeconds the allowed clock skew in seconds, at least 0
     * @throws IllegalArgumentException if the value is negative
     */
    @Value("${dcp.clock-skew-seconds:" + DEFAULT_CLOCK_SKEW_SECONDS + "}")
    void setClockSkewSeconds(long clockSkewSeconds) {
        if (clockSkewSeconds < 0) {
            throw new IllegalArgumentException(
                    "dcp.clock-skew-seconds must not be negative, but was " + clockSkewSeconds);
        }
        this.clockSkewSeconds = clockSkewSeconds;
    }

    /**
     * Test-only setter to override the signing key.
     *
     * @param key The EC key to use for signing instead of the default from KeyService
     */
    void setOverrideSigningKey(ECKey key) {
        this.overrideSigningKey = key;
    }
}
