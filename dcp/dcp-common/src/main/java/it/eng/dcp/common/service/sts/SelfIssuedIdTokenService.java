package it.eng.dcp.common.service.sts;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantKeyProvider;
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
import java.util.List;
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

    /** JWS {@code typ} of access tokens for the Credential Service (RFC 9068 JWT access token). */
    public static final String ACCESS_TOKEN_TYPE = "at+jwt";

    private static final JOSEObjectType ACCESS_TOKEN_JOSE_TYPE = new JOSEObjectType(ACCESS_TOKEN_TYPE);

    private static final String SCOPE_CLAIM = "scope";

    /** Default clock skew in seconds, used when {@code dcp.clock-skew-seconds} is not set. */
    static final long DEFAULT_CLOCK_SKEW_SECONDS = 120;

//    private final String connectorDid;
    private final DidResolverService didResolver;
    private final JtiReplayCache jtiCache;
    private final ParticipantKeyProvider keyProvider;
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
            ParticipantKeyProvider keyProvider,
            DcpAuditEventPublisher auditPublisher) {
//        this.connectorDid = connectorDid;
        this.didResolver = didResolver;
        this.jtiCache = jtiCache;
        this.keyProvider = keyProvider;
        this.auditPublisher = auditPublisher;
    }

    /**
     * Creates and signs a self-issued ID token for the given audience.
     *
     * @param audienceDid The DID of the intended audience (verifier)
     * @param accessToken Optional access token to include in the token claims
     * @param participant the participant that signs; its DID is the issuer and subject
     * @return Signed JWT token as a string
     * @throws IllegalArgumentException if audienceDid is null or blank
     * @throws IllegalStateException if connectorDid is not configured
     * @throws RuntimeException if signing fails
     */
    public String createAndSignToken(String audienceDid, String accessToken, ParticipantContext participant) {
        if (audienceDid == null || audienceDid.isBlank()) {
            throw new IllegalArgumentException("audienceDid required");
        }

        log.debug("Creating token for audience: {}", audienceDid);

        if (participant.did() == null || participant.did().isBlank()) {
            log.error("connectorDid is null or blank!");
            throw new IllegalStateException("connectorDid is not configured. Please set dcp.connector.did property");
        }

        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(300); // 5 minutes expiry
            String jti = UUID.randomUUID().toString();

            JWTClaimsSet.Builder cb = new JWTClaimsSet.Builder();
            cb.issuer(participant.did()).subject(participant.did());
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
                if (keyProvider == null) {
                    throw new IllegalStateException("ParticipantKeyProvider is not available for signing");
                }
                signingJwk = keyProvider.activeSigningKey(participant);
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
     *   <li>Rejects a token typed as an access token ({@code typ} {@value #ACCESS_TOKEN_TYPE})</li>
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
     * @param participant the participant the token must be addressed to
     * @return The validated JWT claims
     * @throws SecurityException if validation or parsing fails
     */
    public JWTClaimsSet validateToken(String token, ParticipantContext participant) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            String issuer = claims.getIssuer();
            String subject = claims.getSubject();
            String aud = claims.getAudience() != null && !claims.getAudience().isEmpty() ? claims.getAudience().get(0) : null;
            String kid = jwt.getHeader().getKeyID();

            log.debug("Validating Self-Issued ID Token: iss={}, kid={}", issuer, kid);

            // 0. An access token (ADR D-TEC-014) is never accepted as a Self-Issued ID Token
            JOSEObjectType type = jwt.getHeader().getType();
            if (type != null && ACCESS_TOKEN_TYPE.equalsIgnoreCase(type.getType())) {
                throw new SecurityException("An access token cannot be used as a Self-Issued ID Token");
            }

            // 1. iss and sub must be the same (using robust DID comparison)
            if (issuer == null || subject == null || !DidUrlConverter.compareDids(issuer, subject)) {
                throw new SecurityException("iss and sub must be present and equal");
            }

            String connectorDid = participant.did();
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
     * <p>Per DCP v1.0.1 §4.3.1, §5.1 and §5.3.1:
     * <ul>
     *   <li>Outer JWT: Self-Issued ID Token (iss==sub, aud = Verifier DID, contains "token" claim)</li>
     *   <li>Inner JWT: Access Token for this participant's Credential Service, bound to the same Verifier
     *       ({@code aud} = Verifier DID), see {@link #createAccessTokenWithScopes(String, ParticipantContext, String...)}</li>
     * </ul>
     *
     * @param audienceDid The DID of the intended audience (verifier)
     * @param participant the participant that signs; its DID is the issuer and subject
     * @param scopes The credential types or scopes the verifier is authorized to access
     * @return Signed JWT token with nested access token containing scopes
     * @throws IllegalArgumentException if audienceDid is null or blank
     * @throws IllegalStateException if connectorDid is not configured
     * @throws RuntimeException if signing fails
     */
    public String createStsCompatibleToken(String audienceDid, ParticipantContext participant, String... scopes) {
        if (audienceDid == null || audienceDid.isBlank()) {
            throw new IllegalArgumentException("audienceDid required");
        }

        log.info("Creating STS-compatible wrapper token for audience: {} with {} scopes",
                 audienceDid, scopes != null ? scopes.length : 0);

        if (participant.did() == null || participant.did().isBlank()) {
            log.error("connectorDid is null or blank!");
            throw new IllegalStateException("connectorDid is not configured. Please set dcp.connector.did property");
        }

        try {
            // The inner access token is bound to the Verifier that will present it back to our Credential Service
            String innerToken = createAccessTokenWithScopes(audienceDid, participant, scopes);

            // Now create a wrapper Self-Issued ID Token that contains the inner token in the "token" claim
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(300); // 5 minutes expiry
            String jti = "urn:uuid:" + UUID.randomUUID();

            JWTClaimsSet.Builder cb = new JWTClaimsSet.Builder();
            cb.issuer(participant.did()).subject(participant.did()); // Self-issued: iss == sub
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
                if (keyProvider == null) {
                    throw new IllegalStateException("ParticipantKeyProvider is not available for signing");
                }
                signingJwk = keyProvider.activeSigningKey(participant);
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
     * Creates an access token for this participant's Credential Service (DCP v1.0.1 §5.3.1).
     *
     * <p>The access token is carried in the {@code token} claim of a Self-Issued ID Token. The Verifier
     * places it, unchanged, in the {@code token} claim of its own Self-Issued ID Token when it queries the
     * Credential Service, which then validates it with
     * {@link #validateAccessToken(String, String, ParticipantContext)}. The format is decided in ADR D-TEC-014:
     * <ul>
     *   <li>JWS header {@code typ} = {@value #ACCESS_TOKEN_TYPE} (RFC 9068), so it cannot be confused with a
     *       Self-Issued ID Token</li>
     *   <li>{@code iss} = {@code sub} = this participant's (the holder's) DID</li>
     *   <li>{@code aud} = the Verifier DID the token is bound to</li>
     *   <li>{@code scope}: the scopes the Verifier may query; an access token without scopes authorizes
     *       no credentials</li>
     *   <li>{@code iat}, {@code nbf}, {@code exp} (5 minutes) and {@code jti}</li>
     * </ul>
     *
     * @param verifierDid the DID of the Verifier the token is bound to
     * @param participant the participant that issues the access token
     * @param scopes the scopes the Verifier is authorized to query
     * @return the signed JWT access token
     * @throws IllegalArgumentException if verifierDid is null or blank
     * @throws IllegalStateException if the holder DID is not configured
     * @throws RuntimeException if signing fails
     */
    public String createAccessTokenWithScopes(String verifierDid, ParticipantContext participant, String... scopes) {
        if (verifierDid == null || verifierDid.isBlank()) {
            throw new IllegalArgumentException("verifierDid required");
        }
        if (participant.did() == null || participant.did().isBlank()) {
            throw new IllegalStateException("connectorDid is not configured. Please set dcp.connector.did property");
        }

        Instant now = Instant.now();
        Instant exp = now.plusSeconds(300); // 5 minutes validity
        String jti = "urn:uuid:" + UUID.randomUUID();

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
            .issuer(participant.did())
            .subject(participant.did()) // the holder is both issuer and subject of its access token
            .audience(verifierDid)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(exp))
            .notBeforeTime(Date.from(now)) // Valid immediately
            .jwtID(jti);

        List<String> scopeList = scopes == null ? List.of()
                : Arrays.stream(scopes).filter(scope -> scope != null && !scope.isBlank()).toList();
        if (!scopeList.isEmpty()) {
            claimsBuilder.claim(SCOPE_CLAIM, scopeList);
            log.debug("Access token scopes: {}", scopeList);
        } else {
            log.warn("Generating access token with no scopes for verifier: {}. "
                    + "It authorizes no credentials at the Credential Service.", verifierDid);
        }

        JWTClaimsSet claims = claimsBuilder.build();

        try {
            ECKey signingJwk = overrideSigningKey != null ? overrideSigningKey : keyProvider.activeSigningKey(participant);

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                    .keyID(signingJwk.getKeyID())
                    .type(ACCESS_TOKEN_JOSE_TYPE)
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            JWSSigner signer = new ECDSASigner(signingJwk.toECPrivateKey());
            jwt.sign(signer);
            String serialized = jwt.serialize();
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_ISSUED,
                    "Access token issued for verifier: " + verifierDid,
                    "common",
                    null, null,
                    scopeList,
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
            throw new RuntimeException("Failed to sign access token", e);
        }
    }

    /**
     * Validates an access token that this participant issued for its own Credential Service.
     *
     * <p>Checks, per ADR D-TEC-014 and DCP v1.0.1 §5.3.1:
     * <ul>
     *   <li>the JWS header {@code typ} is {@value #ACCESS_TOKEN_TYPE}</li>
     *   <li>{@code iss} and {@code sub} both equal this participant's DID: the token was issued by this
     *       Credential Service's holder</li>
     *   <li>{@code aud} contains {@code expectedVerifierDid}: the token is presented by the Verifier it was
     *       issued to, so a token captured by one Verifier cannot be used by another</li>
     *   <li>the signature verifies with a {@code capabilityInvocation} key of this participant's DID
     *       document (the key that signs its Self-Issued ID Tokens)</li>
     *   <li>{@code exp} and {@code iat} are present and current, and {@code nbf} if present has passed,
     *       with the configured clock skew</li>
     * </ul>
     *
     * <p>The {@code jti} is not recorded for replay protection: the access token is always carried inside a
     * Self-Issued ID Token of the Verifier, whose own {@code jti} is checked by {@link #validateToken(String, ParticipantContext)}.
     *
     * @param accessToken the access token taken from the {@code token} claim of the Verifier's Self-Issued ID Token
     * @param expectedVerifierDid the validated {@code sub} of the Verifier's Self-Issued ID Token
     * @param participant the participant that issued the access token
     * @return the validated access token claims
     * @throws SecurityException if the access token is missing or invalid
     */
    public JWTClaimsSet validateAccessToken(String accessToken, String expectedVerifierDid, ParticipantContext participant) {
        try {
            if (accessToken == null || accessToken.isBlank()) {
                throw new SecurityException("Access token is required");
            }
            SignedJWT jwt = SignedJWT.parse(accessToken);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            JOSEObjectType type = jwt.getHeader().getType();
            if (type == null || !ACCESS_TOKEN_TYPE.equalsIgnoreCase(type.getType())) {
                throw new SecurityException("Access token typ must be " + ACCESS_TOKEN_TYPE);
            }

            String ownDid = participant.did();
            String issuer = claims.getIssuer();
            String subject = claims.getSubject();
            if (ownDid == null || issuer == null || subject == null
                    || !DidUrlConverter.compareDids(issuer, ownDid) || !DidUrlConverter.compareDids(subject, ownDid)) {
                throw new SecurityException("Access token was not issued by this Credential Service's holder");
            }

            List<String> audience = claims.getAudience();
            if (expectedVerifierDid == null || audience == null
                    || audience.stream().noneMatch(aud -> DidUrlConverter.compareDids(aud, expectedVerifierDid))) {
                throw new SecurityException("Access token aud does not match the querying Verifier");
            }

            JWK jwk = didResolver.resolvePublicKey(ownDid, jwt.getHeader().getKeyID(), DidDocument.CAPABILITY_INVOCATION);
            if (jwk == null) {
                throw new SecurityException("No JWK found for the access token signing key");
            }
            if (!jwt.verify(createVerifier(jwk))) {
                throw new SecurityException("Invalid access token signature");
            }

            Instant now = Instant.now();
            Date nbf = claims.getNotBeforeTime();
            if (nbf != null && now.isBefore(nbf.toInstant().minusSeconds(clockSkewSeconds))) {
                throw new SecurityException("Access token not valid yet (nbf)");
            }
            Date exp = claims.getExpirationTime();
            if (exp == null || now.isAfter(exp.toInstant().plusSeconds(clockSkewSeconds))) {
                throw new SecurityException("Access token expired");
            }
            Date iat = claims.getIssueTime();
            if (iat == null || now.isBefore(iat.toInstant().minusSeconds(clockSkewSeconds))) {
                throw new SecurityException("Access token iat missing or in the future");
            }

            log.debug("Access token validated for verifier: {}", expectedVerifierDid);
            return claims;
        } catch (SecurityException e) {
            auditAccessTokenRejected(e.getMessage());
            throw e;
        } catch (DidResolutionException e) {
            String reason = "Failed to resolve DID: " + e.getMessage();
            auditAccessTokenRejected(reason);
            throw new SecurityException(reason, e);
        } catch (JOSEException e) {
            auditAccessTokenRejected("Invalid access token signature");
            throw new SecurityException("Invalid access token signature", e);
        } catch (ParseException e) {
            auditAccessTokenRejected("Invalid access token format");
            throw new SecurityException("Invalid access token format", e);
        }
    }

    /**
     * Logs and audits a rejected access token.
     *
     * @param reason the rejection reason, without token content
     */
    private void auditAccessTokenRejected(String reason) {
        log.warn("Access token validation failed: {}", reason);
        auditPublisher.publishEvent(
                DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                "Access token validation failed: " + reason,
                "common",
                null, null, null, null,
                Map.of("reason", reason));
    }

    /**
     * Backward-compatible overload without scopes.
     *
     * @param audienceDid The DID of the intended audience (verifier)
     * @param participant the participant that signs; its DID is the issuer and subject
     * @return Signed JWT token with nested token claim (without scope restrictions)
     * @deprecated Use {@link #createStsCompatibleToken(String, ParticipantContext, String...)} with explicit scopes
     */
    @Deprecated
    public String createStsCompatibleToken(String audienceDid, ParticipantContext participant) {
        log.warn("Creating STS-compatible token WITHOUT scopes - consider specifying scopes for security");
        return createStsCompatibleToken(audienceDid, participant, new String[0]);
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
