package it.eng.dcp.verifier.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.PresentationQueryMessage;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.service.IssuerTrustService;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.common.service.did.HttpDidResolverService;
import it.eng.dcp.common.service.did.ResolvedVerificationMethod;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.common.util.DcpTransportSecurity;
import it.eng.dcp.common.util.DidUrlConverter;
import it.eng.dcp.common.exception.InsecureEndpointException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Service layer for verifier-side operations implementing DCP Presentation Query Authentication Flow.
 *
 * <p>This service handles the verifier's role in the DCP protocol (Steps 3-4):
 * <ul>
 *   <li>Step 3: Validate self-issued ID token from holder, extract access token</li>
 *   <li>Step 4: Query holder's credential service with the Verifier's own Self-Issued ID Token carrying
 *       the access token</li>
 * </ul>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Validate incoming self-issued ID tokens (DSP requests)</li>
 *   <li>Extract access tokens from the "token" claim</li>
 *   <li>Resolve holder's DID to find their credential service endpoint</li>
 *   <li>Query holder's credential service with proper authorization</li>
 * </ul>
 *
 * <p>Uses verifier's DID configuration explicitly via @Qualifier to avoid confusion with holder config.
 *
 * @see <a href="dcp-presentation-authentication-flow.md">DCP Presentation Authentication Flow</a>
 */
@Service
@Slf4j
public class VerifierService {

    private static final String CREDENTIAL_SERVICE_TYPE = "CredentialService";
    private static final String PRESENTATIONS_QUERY_PATH = "/presentations/query";
    private static final String PRESENTATION_CONTEXT = "Presentation";
    private static final String CREDENTIAL_CONTEXT = "Credential";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String VERIFIER_SOURCE = "verifier";

    private final SelfIssuedIdTokenService tokenService;
    private final HttpDidResolverService didResolverService;
    private final SimpleOkHttpRestClient httpClient;
    private final ObjectMapper objectMapper;
    private final DidDocumentConfig didConfig;
    private final IssuerTrustService issuerTrustService;
    private final DcpAuditEventPublisher auditPublisher;

    @Value("${dcp.allow-insecure-http:false}")
    private boolean allowInsecureHttp;

    @Value("${dcp.clock-skew-seconds:120}")
    private long clockSkewSeconds;

    /**
     * Constructor with explicit verifier configuration injection.
     *
     * @param tokenService The token service configured with verifier's DID (for aud validation)
     * @param didResolverService Service for resolving DIDs to DID documents
     * @param httpClient HTTP client for making REST calls
     * @param objectMapper JSON mapper for request/response serialization
     * @param didConfig The verifier configuration (explicitly qualified)
     * @param issuerTrustService Service for checking whether a credential issuer is trusted
     * @param auditPublisher Publisher for DCP audit events
     */
    @Autowired
    public VerifierService(
            @Qualifier("verifierTokenService") SelfIssuedIdTokenService tokenService,
            HttpDidResolverService didResolverService,
            SimpleOkHttpRestClient httpClient,
            ObjectMapper objectMapper,
            DidDocumentConfig didConfig,
            IssuerTrustService issuerTrustService,
            DcpAuditEventPublisher auditPublisher) {
        this.tokenService = tokenService;
        this.didResolverService = didResolverService;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.didConfig = didConfig;
        this.issuerTrustService = issuerTrustService;
        this.auditPublisher = auditPublisher;

        log.info("VerifierService initialized with verifier DID: {}", didConfig.getDid());
    }


    /**
     * STEP 3: Validate self-issued ID token and extract access token.
     *
     * <p>This implements Step 3 of the DCP flow:
     * <ul>
     *   <li>Validates the self-issued ID token (iss==sub, aud==verifier DID, signature)</li>
     *   <li>Extracts the "token" claim containing the access token</li>
     *   <li>Takes the presenting party's DID from the validated {@code sub} claim (DCP §4.3.3)</li>
     * </ul>
     *
     * @param bearerToken The self-issued ID token from Authorization header (without "Bearer " prefix)
     * @return The validated {@code sub} DID and the access token extracted from the "token" claim
     * @throws SecurityException if token is invalid, has no {@code sub}, or "token" claim is missing
     */
    private ValidatedSelfIssuedToken validateAndExtractAccessToken(String bearerToken) throws SecurityException {
        if (bearerToken == null || bearerToken.isBlank()) {
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Self-issued ID token validation failed: bearer token is missing",
                    VERIFIER_SOURCE,
                    null, null, null, null,
                    Map.of("reason", "Bearer token is required"));
            throw new SecurityException("Bearer token is required");
        }

        log.debug("STEP 3: Validating self-issued ID token for verifier DID: {}", didConfig.getDid());

        // Validate the self-issued ID token
        // This checks: iss==sub, aud==verifier DID, signature, expiry
        JWTClaimsSet claims = tokenService.validateToken(bearerToken);

        // The presenting party is the validated sub; never a DID taken from the unverified access token
        String subjectDid = claims.getSubject();
        if (subjectDid == null || subjectDid.isBlank()) {
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Self-issued ID token validation failed: sub claim is missing",
                    VERIFIER_SOURCE,
                    null, null, null, null,
                    Map.of("reason", "Self-issued ID token must contain a 'sub' claim"));
            throw new SecurityException("Self-issued ID token must contain a 'sub' claim");
        }

        // Extract the access token from "token" claim
        String accessToken;
        try {
            accessToken = claims.getStringClaim("token");
        } catch (java.text.ParseException e) {
            log.error("Failed to parse 'token' claim from self-issued ID token", e);
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Failed to parse 'token' claim from self-issued ID token",
                    VERIFIER_SOURCE,
                    null, null, null, null,
                    Map.of("reason", e.getMessage()));
            throw new SecurityException("Invalid 'token' claim in self-issued ID token");
        }

        if (accessToken == null || accessToken.isBlank()) {
            log.error("Self-issued ID token missing 'token' claim");
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Self-issued ID token missing 'token' claim",
                    VERIFIER_SOURCE,
                    null, null, null, null,
                    Map.of("reason", "Self-issued ID token must contain 'token' claim with access token"));
            throw new SecurityException("Self-issued ID token must contain 'token' claim with access token");
        }

        log.debug("Successfully extracted access token from self-issued ID token");
        auditPublisher.publishEvent(
                DcpAuditEventType.SELF_ISSUED_TOKEN_VALIDATED,
                "Self-issued ID token validated, access token extracted",
                VERIFIER_SOURCE,
                null, null, null, null,
                Map.of("holderDid", subjectDid));
        return new ValidatedSelfIssuedToken(subjectDid, accessToken);
    }

    /**
     * Checks that the access token was not issued by a party other than the presenting party.
     *
     * <p>The access token is not verified by the Verifier, so its {@code iss} is never used as the
     * holder DID. It is only compared with the validated Self-Issued ID Token {@code sub}: an access
     * token issued by a different DID is a token wrapped by another participant and is rejected. An
     * absent {@code iss} is accepted, since the holder DID does not depend on it. The access token
     * {@code sub} is not compared: its content is implementation-defined (other DCP implementations
     * set it to the Verifier DID), and the holder DID never depends on it.
     *
     * @param accessTokenClaims the claims of the access token carried in the "token" claim
     * @param holderDid the validated {@code sub} of the Self-Issued ID Token
     * @throws SecurityException if the access token {@code iss} names a different DID
     */
    private void requireAccessTokenIssuedByHolder(JWTClaimsSet accessTokenClaims, String holderDid) {
        String accessTokenIssuer = accessTokenClaims.getIssuer();
        if (accessTokenIssuer != null && !DidUrlConverter.compareDids(accessTokenIssuer, holderDid)) {
            throw new SecurityException("Access token 'iss' does not match the Self-Issued ID Token 'sub'");
        }
    }

    /**
     * STEP 3 (continued): Resolve holder's DID to find their credential service endpoint.
     *
     * <p>Fetches the holder's DID document and extracts the CredentialService endpoint URL.
     *
     * @param holderDid The holder's DID (the validated sub claim of the self-issued ID token)
     * @return The holder's credential service endpoint URL
     * @throws DidResolutionException if DID cannot be resolved or service endpoint not found
     */
    private String resolveHolderCredentialService(String holderDid) throws DidResolutionException {
        log.debug("STEP 3: Resolving holder DID to find credential service: {}", holderDid);

        // Fetch holder's DID document
        DidDocument holderDidDoc;
        try {
            holderDidDoc = didResolverService.fetchDidDocumentCached(holderDid);
        } catch (IOException e) {
            throw new DidResolutionException("Failed to fetch holder DID document: " + holderDid, e);
        }

        if (holderDidDoc == null) {
            throw new DidResolutionException("Failed to resolve holder DID: " + holderDid);
        }

        // Find CredentialService endpoint
        List<ServiceEntry> services = holderDidDoc.getServices();
        if (services == null || services.isEmpty()) {
            throw new DidResolutionException("Holder DID document has no service endpoints: " + holderDid);
        }

        for (ServiceEntry service : services) {
            if (CREDENTIAL_SERVICE_TYPE.equals(service.type())) {
                String endpoint = service.serviceEndpoint();
                log.debug("Found holder credential service endpoint: {}", endpoint);
                try {
                    DcpTransportSecurity.requireSecure(endpoint,
                            "CredentialService endpoint of " + holderDid, allowInsecureHttp);
                } catch (InsecureEndpointException e) {
                    throw new DidResolutionException(e.getMessage(), e);
                }
                return endpoint;
            }
        }

        throw new DidResolutionException(
            "Holder DID document missing CredentialService endpoint: " + holderDid);
    }

    /**
     * STEP 4: Query holder's credential service for verifiable presentations.
     *
     * <p>Sends a POST request to {holder-credential-service}/presentations/query. Per DCP v1.0.1 §5.1
     * step 6 and §5.3.1, the Verifier authenticates with a Self-Issued ID Token of its own
     * ({@code iss} = {@code sub} = Verifier DID, {@code aud} = holder DID) that carries the access token
     * it received from the holder, unchanged, in its {@code token} claim.
     *
     * @param credentialServiceUrl The holder's credential service base URL
     * @param holderDid The holder's DID, the audience of the Verifier's Self-Issued ID Token
     * @param accessToken The access token (from "token" claim of the holder's self-issued ID token)
     * @param presentationQueryMessage The query message with scopes
     * @return The presentation response from holder
     * @throws IOException if HTTP request fails
     * @throws SecurityException if access token is rejected by holder
     */
    private PresentationResponseMessage queryHolderPresentations(
            String credentialServiceUrl,
            String holderDid,
            String accessToken,
            PresentationQueryMessage presentationQueryMessage) throws IOException, SecurityException {

        String queryUrl = credentialServiceUrl + PRESENTATIONS_QUERY_PATH;
        log.debug("STEP 4: Querying holder credential service: {}", queryUrl);

        // The Verifier's own Self-Issued ID Token, bound to the holder, carrying the access token
        String verifierIdToken = tokenService.createAndSignToken(holderDid, accessToken, didConfig);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + verifierIdToken);
        headers.put("Content-Type", "application/json");

        // Serialize query message to JSON
        String jsonBody = objectMapper.writeValueAsString(presentationQueryMessage);
        RequestBody requestBody = RequestBody.create(jsonBody, JSON);

        log.debug("Sending presentation query with the Verifier's Self-Issued ID Token to holder");

        // Execute request
        PresentationResponseMessage response = httpClient.executeAndDeserialize(
            queryUrl,
            "POST",
            headers,
            requestBody,
            PresentationResponseMessage.class
        );

        if (response == null) {
            throw new SecurityException("Holder rejected presentation query - invalid access token or authorization failure");
        }

        log.info("Successfully received presentation response from holder");
        return response;
    }

    /**
     * STEP 5: Validate the presentation response from holder.
     *
     * <p>This validates the presentation JWT(s) received from the holder:
     * <ul>
     *   <li>Decodes each presentation JWT</li>
     *   <li>Verifies holder DID matches expected holder</li>
     *   <li>Validates the presentation proof with a key of the holder DID that has the {@code authentication}
     *       verification relationship</li>
     *   <li>Extracts embedded verifiable credentials from 'vp.verifiableCredential' claim</li>
     *   <li>Validates each credential proof with an {@code assertionMethod} key of the credential issuer</li>
     *   <li>Verifies credential claims (iss, sub, exp, etc.)</li>
     * </ul>
     *
     * @param presentationResponse The presentation response from holder
     * @param expectedHolderDid The expected holder DID (the validated Self-Issued ID Token sub)
     * @param expectedVerifierDid The verifier DID that the presentation must target
     * @return List of validated presentations with their credentials
     * @throws SecurityException if validation fails
     */
    private List<ValidatedPresentation> validatePresentationResponse(
            PresentationResponseMessage presentationResponse,
            String expectedHolderDid,
            String expectedVerifierDid) throws SecurityException {

        log.debug("STEP 5: Validating presentation response from holder: {}", expectedHolderDid);

        List<Object> presentations = presentationResponse.getPresentation();
        if (presentations == null || presentations.isEmpty()) {
            throw new SecurityException("Presentation response contains no presentations");
        }

        List<ValidatedPresentation> validatedPresentations = new ArrayList<>();

        for (Object presentationObj : presentations) {
            if (!(presentationObj instanceof String presentationJwt)) {
                log.warn("Skipping non-JWT presentation: {}", presentationObj.getClass());
                continue;
            }

            try {
                // Parse outer presentation JWT
                SignedJWT presentationSignedJwt = SignedJWT.parse(presentationJwt);
                JWTClaimsSet presentationClaims = presentationSignedJwt.getJWTClaimsSet();

                // Extract holder DID from iss/sub claims
                String holderDid = presentationClaims.getIssuer();
                if (holderDid == null || holderDid.isBlank()) {
                    holderDid = presentationClaims.getSubject();
                }

                if (holderDid == null || holderDid.isBlank()) {
                    throw new SecurityException("Presentation JWT missing holder DID (iss/sub claim)");
                }

                // Verify holder DID matches the presenting party (validated Self-Issued ID Token sub)
                if (!DidUrlConverter.compareDids(holderDid, expectedHolderDid)) {
                    throw new SecurityException(String.format(
                        "Presentation holder DID mismatch: expected=%s, actual=%s",
                        expectedHolderDid, holderDid));
                }

                log.debug("Presentation holder DID verified: {}", holderDid);
                validatePresentationBinding(presentationClaims, expectedVerifierDid);

                // Verify the VP proof with an authentication key of the holder (DCP §5.4.3)
                verifyProof(presentationSignedJwt, holderDid, DidDocument.AUTHENTICATION, PRESENTATION_CONTEXT);

                log.debug("Presentation JWT signature verified");

                // Extract VP claim containing the presentation data
                Object vpClaimObj = presentationClaims.getClaim("vp");
                if (vpClaimObj == null) {
                    throw new SecurityException("Presentation JWT missing 'vp' claim");
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> vpClaim = (Map<String, Object>) vpClaimObj;

                // Extract verifiable credentials from VP
                Object verifiableCredentialObj = vpClaim.get("verifiableCredential");
                if (verifiableCredentialObj == null) {
                    log.warn("Presentation has no verifiableCredential array");
                    continue;
                }

                if (!(verifiableCredentialObj instanceof List)) {
                    throw new SecurityException("VP verifiableCredential must be an array");
                }

                @SuppressWarnings("unchecked")
                List<Object> verifiableCredentials = (List<Object>) verifiableCredentialObj;

                // Validate each embedded credential
                List<ValidatedCredential> validatedCredentials = new ArrayList<>();
                for (Object credentialObj : verifiableCredentials) {
                    if (!(credentialObj instanceof String credentialJwt)) {
                        log.warn("Skipping non-JWT credential: {}", credentialObj.getClass());
                        continue;
                    }

                    ValidatedCredential validatedCredential = validateCredential(credentialJwt, holderDid);
                    validatedCredentials.add(validatedCredential);
                }

                validatedPresentations.add(new ValidatedPresentation(
                    holderDid,
                    presentationClaims,
                    validatedCredentials
                ));

                log.info("✓ Presentation validated successfully with {} credentials", validatedCredentials.size());

            } catch (ParseException e) {
                log.error("Failed to parse presentation JWT: {}", e.getMessage());
                throw new SecurityException("Invalid presentation JWT format: " + e.getMessage());
            } catch (Exception e) {
                log.error("Presentation validation failed: {}", e.getMessage());
                throw new SecurityException("Presentation validation failed: " + e.getMessage());
            }
        }

        if (validatedPresentations.isEmpty()) {
            throw new SecurityException("No valid presentations found in response");
        }

        return validatedPresentations;
    }

    /**
     * Validates the audience and expiration claims of a presentation.
     *
     * @param presentationClaims the signed presentation claims
     * @param expectedVerifierDid the verifier DID that queried the holder
     * @throws SecurityException if the audience or expiration is invalid
     */
    private void validatePresentationBinding(JWTClaimsSet presentationClaims,
                                             String expectedVerifierDid) throws SecurityException {
        List<String> audiences = presentationClaims.getAudience();
        if (audiences == null || audiences.stream()
                .noneMatch(audience -> DidUrlConverter.compareDids(audience, expectedVerifierDid))) {
            throw new SecurityException("Presentation audience does not match verifier DID");
        }

        Date expiration = presentationClaims.getExpirationTime();
        if (expiration == null
                || Instant.now().isAfter(expiration.toInstant().plusSeconds(clockSkewSeconds))) {
            throw new SecurityException("Presentation expired");
        }
    }

    /**
     * Validate an embedded verifiable credential JWT.
     *
     * <p>Validates:
     * <ul>
     *   <li>Issuer DID presence (iss claim)</li>
     *   <li>JWT signature with an {@code assertionMethod} key whose verification method DID equals the issuer</li>
     *   <li>The {@code issuer} property, when present, names the same DID as {@code iss}</li>
     *   <li>Subject matches holder DID</li>
     *   <li>Expiration date (exp claim)</li>
     *   <li>Issuance date (iat claim)</li>
     *   <li>Issuer is trusted for the credential type (via {@link IssuerTrustService})</li>
     * </ul>
     *
     * @param credentialJwt the credential JWT string
     * @param expectedHolderDid the expected holder DID (subject of credential)
     * @return ValidatedCredential containing credential claims and metadata
     * @throws SecurityException if any validation step fails, including an untrusted issuer
     */
    private ValidatedCredential validateCredential(String credentialJwt, String expectedHolderDid)
            throws SecurityException {

        try {
            // Parse credential JWT
            SignedJWT credentialSignedJwt = SignedJWT.parse(credentialJwt);
            JWTClaimsSet credentialClaims = credentialSignedJwt.getJWTClaimsSet();

            // Extract issuer DID
            String issuerDid = credentialClaims.getIssuer();
            if (issuerDid == null || issuerDid.isBlank()) {
                throw new SecurityException("Credential JWT missing issuer (iss claim)");
            }

            log.debug("Validating credential from issuer: {}", issuerDid);

            // Verify the VC proof with an assertionMethod key controlled by the issuer (DCP §5.4.3)
            verifyProof(credentialSignedJwt, issuerDid, DidDocument.ASSERTION_METHOD, CREDENTIAL_CONTEXT);

            log.debug("✓ Credential JWT signature verified for issuer: {}", issuerDid);

            // The VC issuer property, when present, must name the same DID as iss (DCP §5.4.3)
            requireIssuerPropertyMatches(credentialClaims, issuerDid);

            // Holder binding: the credential subject must be the participant presenting it (DCP §5.4.3)
            verifyHolderBinding(credentialClaims, expectedHolderDid);

            // Check expiration
            if (credentialClaims.getExpirationTime() != null) {
                if (credentialClaims.getExpirationTime().toInstant().isBefore(java.time.Instant.now())) {
                    throw new SecurityException("Credential expired");
                }
            }

            // Check issuance date
            if (credentialClaims.getIssueTime() != null) {
                if (credentialClaims.getIssueTime().toInstant().isAfter(java.time.Instant.now())) {
                    throw new SecurityException("Credential issued in the future");
                }
            }

            // Extract credential type
            String credentialType = extractCredentialType(credentialClaims);

            // Verify the issuer is trusted for this credential type
            if (!issuerTrustService.isTrusted(credentialType, issuerDid)) {
                throw new SecurityException(String.format(
                    "Credential issuer not trusted: issuer=%s, credentialType=%s. " +
                    "Configure dcp.trusted-issuers.%s=<issuerDid> to trust this issuer.",
                    issuerDid, credentialType, credentialType));
            }

            log.debug("✓ Credential validated: type={}, issuer={}", credentialType, issuerDid);

            return new ValidatedCredential(issuerDid, credentialType, credentialClaims);

        } catch (ParseException e) {
            log.error("Failed to parse credential JWT: {}", e.getMessage());
            throw new SecurityException("Invalid credential JWT format: " + e.getMessage());
        } catch (Exception e) {
            log.error("Credential validation failed: {}", e.getMessage());
            throw new SecurityException("Credential validation failed: " + e.getMessage());
        }
    }

    /**
     * Enforces holder binding: every credential subject must be the holder that presented the credential.
     *
     * <p>Supports both JWT shapes, and checks every {@code credentialSubject} present: VC 1.1 nests it under
     * the {@code vc} claim, VC 2.0 carries it as a top-level claim; each may be a single object or an array.
     * In the VC 1.1 JWT encoding {@code sub} carries {@code credentialSubject.id}, so a single VC 1.1 subject
     * without {@code id} takes {@code sub} as its ID. A {@code sub} claim, when present, must also equal the
     * holder DID. Subject IDs are compared as DIDs; a DID URL with a fragment is a different resource and
     * does not match.
     *
     * @param credentialClaims the claims of the credential JWT
     * @param expectedHolderDid the DID of the presenting holder
     * @throws SecurityException if a subject ID is missing or differs from the holder DID
     */
    private void verifyHolderBinding(JWTClaimsSet credentialClaims, String expectedHolderDid) {
        String sub = credentialClaims.getSubject();
        List<Object> vc11Subjects = credentialClaims.getClaim("vc") instanceof Map<?, ?> vcClaim
                ? asList(vcClaim.get("credentialSubject")) : List.of();
        List<Object> vc20Subjects = asList(credentialClaims.getClaim("credentialSubject"));
        if (vc11Subjects.isEmpty() && vc20Subjects.isEmpty()) {
            throw new SecurityException("Credential subject id missing: credential has no credentialSubject");
        }

        // VC 1.1 JWT encoding: sub replaces credentialSubject.id of a single subject
        boolean subStandsInForId = vc11Subjects.size() == 1 && sub != null && !sub.isBlank();
        for (Object subjectEntry : vc11Subjects) {
            checkSubjectBoundToHolder(subjectEntry, subStandsInForId ? sub : null, expectedHolderDid);
        }
        for (Object subjectEntry : vc20Subjects) {
            checkSubjectBoundToHolder(subjectEntry, null, expectedHolderDid);
        }

        if (sub != null && !isSameDid(sub, expectedHolderDid)) {
            throw new SecurityException(String.format(
                    "Credential subject mismatch: expected=%s, actual=%s", expectedHolderDid, sub));
        }
    }

    /**
     * Checks that the {@code issuer} property of a credential names the same DID as its {@code iss} claim.
     *
     * <p>The property is read where each JWT shape carries it: under the {@code vc} claim (VC 1.1) and at
     * the top level (VC 2.0). It may be a DID string or an object with an {@code id}. An absent property is
     * accepted, since the VC-JWT encoding lets {@code iss} stand in for it. Because the proof key must belong
     * to {@code iss}, this check makes the verification method DID equal the VC issuer.
     *
     * @param credentialClaims the claims of the credential JWT
     * @param issDid the {@code iss} claim of the credential JWT
     * @throws SecurityException if an {@code issuer} property names another DID or has no {@code id}
     */
    private static void requireIssuerPropertyMatches(JWTClaimsSet credentialClaims, String issDid) {
        List<Object> issuerProperties = new ArrayList<>();
        if (credentialClaims.getClaim("vc") instanceof Map<?, ?> vcClaim && vcClaim.get("issuer") != null) {
            issuerProperties.add(vcClaim.get("issuer"));
        }
        if (credentialClaims.getClaim("issuer") != null) {
            issuerProperties.add(credentialClaims.getClaim("issuer"));
        }
        for (Object issuerProperty : issuerProperties) {
            Object id = issuerProperty instanceof Map<?, ?> issuerMap ? issuerMap.get("id") : issuerProperty;
            if (!(id instanceof String issuerId) || !isSameDid(issuerId, issDid)) {
                throw new SecurityException(String.format(
                        "Credential issuer mismatch: iss=%s, issuer=%s", issDid, id));
            }
        }
    }

    private static List<Object> asList(Object claim) {
        if (claim == null) {
            return List.of();
        }
        if (claim instanceof List<?> list) {
            return List.copyOf(list);
        }
        return List.of(claim);
    }

    private static void checkSubjectBoundToHolder(Object subjectEntry, String fallbackId, String expectedHolderDid) {
        Object id = subjectEntry instanceof Map<?, ?> subjectMap ? subjectMap.get("id") : null;
        String subjectId = id instanceof String text && !text.isBlank() ? text : fallbackId;
        if (subjectId == null) {
            throw new SecurityException("Credential subject id missing: holder binding cannot be verified");
        }
        if (!isSameDid(subjectId, expectedHolderDid)) {
            throw new SecurityException(String.format(
                    "Credential subject mismatch: expected=%s, actual=%s", expectedHolderDid, subjectId));
        }
    }

    /**
     * Compares two DIDs, tolerating encoding differences but not fragments: {@code did:web:h#key} names a
     * resource of {@code did:web:h}, not the participant itself.
     *
     * @param candidate the DID to check
     * @param expectedDid the expected DID
     * @return true if both name the same DID and the candidate has no fragment
     */
    private static boolean isSameDid(String candidate, String expectedDid) {
        return candidate.indexOf('#') < 0 && DidUrlConverter.compareDids(candidate, expectedDid);
    }

    /**
     * Verifies the proof of a presentation or credential JWT (DCP §5.4.3).
     *
     * <p>Resolves the verification method referenced by the JWT {@code kid} from the signer's DID document.
     * The method must be referenced by the given verification relationship ({@code authentication} for a
     * Verifiable Presentation, {@code assertionMethod} for a Verifiable Credential), its DID part must equal
     * the signer DID, and its key must verify the JWT signature.
     *
     * @param signedJwt the signed JWT to verify
     * @param signerDid the DID that must control the signing key: the holder for a VP, the issuer for a VC
     * @param verificationRelationship the verification relationship the key must have
     * @param contextName context name for messages ({@code Presentation} or {@code Credential})
     * @throws SecurityException if no suitable key exists, the key belongs to another DID, or the signature
     *         does not verify
     */
    private void verifyProof(SignedJWT signedJwt, String signerDid, String verificationRelationship,
            String contextName) throws SecurityException {
        String kid = signedJwt.getHeader().getKeyID();
        ResolvedVerificationMethod method;
        try {
            method = didResolverService.resolveVerificationMethod(signerDid, kid, verificationRelationship)
                    .orElseThrow(() -> new SecurityException(String.format(
                            "%s signature validation failed: No %s key found for DID=%s, kid=%s",
                            contextName, verificationRelationship, signerDid, kid)));
        } catch (DidResolutionException e) {
            throw new SecurityException(String.format(
                    "%s signature validation failed: Cannot resolve DID=%s - %s",
                    contextName, signerDid, e.getMessage()));
        }

        if (!DidUrlConverter.compareDids(method.id(), signerDid)) {
            throw new SecurityException(String.format(
                    "%s signature validation failed: verification method %s does not belong to DID=%s",
                    contextName, method.id(), signerDid));
        }

        if (!(method.publicKey() instanceof ECKey ecPublicKey)) {
            throw new SecurityException(String.format(
                    "%s signature validation failed: unsupported key type %s for DID=%s",
                    contextName, method.publicKey().getKeyType(), signerDid));
        }

        try {
            JWSVerifier verifier = new ECDSAVerifier(ecPublicKey.toECPublicKey());
            if (!signedJwt.verify(verifier)) {
                throw new SecurityException(String.format(
                        "%s signature validation failed: Invalid signature for DID=%s", contextName, signerDid));
            }
        } catch (JOSEException e) {
            throw new SecurityException(String.format(
                    "%s signature validation failed: %s", contextName, e.getMessage()));
        }

        log.debug("✓ {} JWT signature verified for DID: {}", contextName, signerDid);
    }

    /**
     * Extract credential type from JWT claims.
     * Tries nested vc.type array first, then falls back to top-level type claim.
     *
     * @param claims JWT claims set
     * @return Credential type string
     */
    private String extractCredentialType(JWTClaimsSet claims) {
        try {
            // Try nested vc.type (VC 1.1 format)
            Object vcClaimObj = claims.getClaim("vc");
            if (vcClaimObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> vcClaim = (Map<String, Object>) vcClaimObj;
                Object typeObj = vcClaim.get("type");
                if (typeObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> types = (List<String>) typeObj;
                    // Return first non-"VerifiableCredential" type
                    for (String type : types) {
                        if (!"VerifiableCredential".equals(type)) {
                            return type;
                        }
                    }
                }
            }

            // Fallback to top-level type claim
            Object typeObj = claims.getClaim("type");
            if (typeObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> types = (List<String>) typeObj;
                if (!types.isEmpty()) {
                    return types.get(0);
                }
            } else if (typeObj instanceof String) {
                return (String) typeObj;
            }

        } catch (Exception e) {
            log.warn("Failed to extract credential type: {}", e.getMessage());
        }

        return "VerifiableCredential";
    }

    /**
     * MAIN FLOW: Complete DCP presentation authentication flow (Steps 3-5).
     *
     * <p>This is the primary method that orchestrates the entire verifier-side flow:
     * <ol>
     *   <li>Validate self-issued ID token from holder (Step 3a)</li>
     *   <li>Extract access token from "token" claim (Step 3a)</li>
     *   <li>Take the holder DID from the validated Self-Issued ID Token {@code sub} (Step 3a)</li>
     *   <li>Parse access token to extract scopes and reject it if another DID issued it (Step 3b)</li>
     *   <li>Resolve holder's credential service endpoint (Step 3c)</li>
     *   <li>Build PresentationQueryMessage with scopes from access token (Step 4)</li>
     *   <li>Query holder's credential service with the Verifier's own Self-Issued ID Token, which carries
     *       the access token in its {@code token} claim (Step 4)</li>
     *   <li>Validate presentation JWTs and embedded credentials (Step 5)</li>
     * </ol>
     *
     * <p>On ANY failure, logs detailed reason and throws SecurityException with HTTP 403.
     *
     * <p><b>This is the main entry point for verifier controllers.</b>
     *
     * <p><b>Usage Example:</b>
     * <pre>
     * {@code
     * @PostMapping("/negotiations/{id}/verify")
     * public ResponseEntity<?> verifyNegotiation(
     *         @RequestHeader("Authorization") String authHeader) {
     *     String token = authHeader.replace("Bearer ", "");
     *
     *     try {
     *         PresentationFlowResult result = verifierService
     *             .validateAndQueryHolderPresentations(token);
     *
     *         // Process presentations
     *         return ResponseEntity.ok(result.getPresentationResponse());
     *
     *     } catch (SecurityException e) {
     *         return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
     *     } catch (IOException e) {
     *         return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Network error");
     *     }
     * }
     * }
     * </pre>
     *
     * @param selfIssuedIdToken The JWT from Authorization header (without "Bearer " prefix)
     * @return PresentationFlowResult containing all flow data and the presentation response
     * @throws SecurityException if validation fails (HTTP 403 semantics)
     * @throws IOException if network/HTTP errors occur
     */
    public PresentationFlowResult validateAndQueryHolderPresentations(
            String selfIssuedIdToken) throws SecurityException, IOException {

        log.info("═══════════════════════════════════════════════════════════");
        log.info("MAIN FLOW: Starting complete DCP presentation authentication");
        log.info("Verifier DID: {}", didConfig.getDid());
        log.info("═══════════════════════════════════════════════════════════");

        // ═════════════════════════════════════════════════════════════
        // STEP 3a: Validate self-issued ID token and extract access token
        // ═════════════════════════════════════════════════════════════
        String accessToken;
        String holderDid;
        try {
            log.info("STEP 3a: Validating self-issued ID token...");
            ValidatedSelfIssuedToken validatedToken = validateAndExtractAccessToken(selfIssuedIdToken);
            accessToken = validatedToken.accessToken();
            holderDid = validatedToken.subjectDid();
            log.info("STEP 3a: ✓ Self-issued ID token valid, access token extracted");
        } catch (SecurityException e) {
            log.error("STEP 3a FAILED: {}", e.getMessage());
            throw new SecurityException("Step 3a failed - " + e.getMessage());
        }

        // ═════════════════════════════════════════════════════════════
        // STEP 3b: Parse access token to extract scopes
        // ═════════════════════════════════════════════════════════════
        List<String> scopes;
        try {
            log.info("STEP 3b: Parsing access token to extract scopes...");

            // Parse access token JWT
            SignedJWT accessTokenJwt = SignedJWT.parse(accessToken);
            JWTClaimsSet accessTokenClaims = accessTokenJwt.getJWTClaimsSet();

            // The holder DID is the validated sub; an access token issued by another DID is rejected
            requireAccessTokenIssuedByHolder(accessTokenClaims, holderDid);
            log.debug("Holder DID: {}", holderDid);

            // Extract scopes from access token
            Object scopeClaim = accessTokenClaims.getClaim("scope");
            if (scopeClaim == null) {
                log.warn("Access token has no 'scope' claim - using empty scope list");
                scopes = List.of();
            } else if (scopeClaim instanceof List) {
                scopes = (List<String>) scopeClaim;
            } else if (scopeClaim instanceof String scopeString) {
                // Handle space-separated scopes (OAuth2 style)
                scopes = scopeString.isBlank() ? List.of() : List.of(scopeString.split("\\s+"));
            } else {
                log.warn("Unexpected scope claim type: {} - using empty scope list", scopeClaim.getClass());
                scopes = List.of();
            }

            log.info("STEP 3b: ✓ Access token parsed - Scopes: {}", scopes);

        } catch (SecurityException e) {
            log.error("STEP 3b FAILED: Access token is not bound to the presenting party - {}", e.getMessage());
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Access token rejected: " + e.getMessage(),
                    VERIFIER_SOURCE,
                    holderDid, null, null, null,
                    Map.of("reason", e.getMessage()));
            throw new SecurityException("Step 3b failed - " + e.getMessage());
        } catch (Exception e) {
            log.error("STEP 3b FAILED: Failed to parse access token - {}", e.getMessage());
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Access token rejected: cannot parse access token",
                    VERIFIER_SOURCE,
                    holderDid, null, null, null,
                    Map.of("reason", String.valueOf(e.getMessage())));
            throw new SecurityException("Step 3b failed - Cannot parse access token: " + e.getMessage());
        }

        // ═════════════════════════════════════════════════════════════
        // STEP 3c: Resolve holder's DID to find credential service endpoint
        // ═════════════════════════════════════════════════════════════
        String credentialServiceUrl;
        try {
            log.info("STEP 3c: Resolving holder DID to find credential service...");
            credentialServiceUrl = resolveHolderCredentialService(holderDid);
            log.info("STEP 3c: ✓ Credential service resolved: {}", credentialServiceUrl);
        } catch (DidResolutionException e) {
            log.error("STEP 3c FAILED: Cannot resolve holder DID - {}", e.getMessage());
            throw new SecurityException("Step 3c failed - " + e.getMessage());
        }

        // ═════════════════════════════════════════════════════════════
        // STEP 4: Build PresentationQueryMessage and query holder
        // ═════════════════════════════════════════════════════════════
        try {
            log.info("STEP 4: Building PresentationQueryMessage with scopes: {}", scopes);

            // Build query message with scopes from access token using Builder pattern
            PresentationQueryMessage queryMessage = PresentationQueryMessage.Builder.newInstance()
                    .scope(scopes)
                    .build();

            log.info("STEP 4: Querying holder credential service...");
            log.debug("Query URL: {}", credentialServiceUrl + PRESENTATIONS_QUERY_PATH);
            log.debug("Scopes requested: {}", scopes);

            // Publish audit event: presentation query dispatched
            auditPublisher.publishEvent(
                    DcpAuditEventType.PRESENTATION_QUERY_SENT,
                    "Presentation query dispatched to holder credential service",
                    VERIFIER_SOURCE,
                    holderDid, null, scopes, null,
                    Map.of("credentialServiceUrl", credentialServiceUrl, "scopes", scopes));

            // Query holder with access token
            PresentationResponseMessage presentationResponse = queryHolderPresentations(
                credentialServiceUrl,
                holderDid,
                accessToken,
                queryMessage
            );

            if (presentationResponse == null) {
                log.error("STEP 4 FAILED: Holder returned null response");
                throw new SecurityException("Holder rejected query or returned invalid response");
            }

            if(presentationResponse.getPresentation().isEmpty()) {
                log.error("STEP 4 FAILED: Holder returned empty presentation");
                throw new ResponseStatusException(BAD_REQUEST, "Holder returned empty presentation");
            }

            log.info("STEP 4: ✓ Presentation response received from holder");

            // ═════════════════════════════════════════════════════════════
            // STEP 5: Validate presentation JWTs and embedded credentials
            // ═════════════════════════════════════════════════════════════
            List<ValidatedPresentation> validatedPresentations;
            try {
                log.info("STEP 5: Validating presentation JWTs and embedded credentials...");
                validatedPresentations = validatePresentationResponse(
                        presentationResponse, holderDid, didConfig.getDid());
                log.info("STEP 5: ✓ Validated {} presentation(s) with credentials", validatedPresentations.size());

                // Log summary of validated credentials
                for (ValidatedPresentation vp : validatedPresentations) {
                    log.debug("  Holder: {}", vp.getHolderDid());
                    for (ValidatedCredential vc : vp.getCredentials()) {
                        log.debug("    ✓ Credential: type={}, issuer={}", vc.getCredentialType(), vc.getIssuerDid());
                    }
                }

                List<String> credentialTypes = validatedPresentations.stream()
                        .flatMap(vp -> vp.getCredentials().stream())
                        .map(ValidatedCredential::getCredentialType)
                        .distinct()
                        .toList();
                auditPublisher.publishEvent(
                        DcpAuditEventType.PRESENTATION_VERIFIED,
                        "Presentation validated: " + validatedPresentations.size() + " presentation(s) verified",
                        VERIFIER_SOURCE,
                        holderDid, null, credentialTypes, null,
                        Map.of("presentationCount", validatedPresentations.size()));

            } catch (SecurityException e) {
                log.error("STEP 5 FAILED: Presentation validation failed - {}", e.getMessage());
                auditPublisher.publishEvent(
                        DcpAuditEventType.PRESENTATION_INVALID,
                        "Presentation validation failed: " + e.getMessage(),
                        VERIFIER_SOURCE,
                        holderDid, null, null, null,
                        Map.of("reason", e.getMessage()));
                throw new SecurityException("Step 5 failed - Presentation validation: " + e.getMessage());
            }

            log.info("═══════════════════════════════════════════════════════════");
            log.info("✓✓✓ MAIN FLOW COMPLETE - All steps passed successfully");
            log.info("═══════════════════════════════════════════════════════════");

            // Return complete result
            return new PresentationFlowResult(
                accessToken,
                holderDid,
                credentialServiceUrl,
                scopes,
                presentationResponse,
                validatedPresentations
            );

        } catch (IOException e) {
            log.error("STEP 4 FAILED: Network error querying holder - {}", e.getMessage());
            throw new IOException("Step 4 failed - Network error: " + e.getMessage(), e);
        } catch (SecurityException e) {
            log.error("STEP 4 FAILED: Holder rejected query - {}", e.getMessage());
            throw new SecurityException("Step 4 failed - " + e.getMessage());
        }
    }

    /**
     * Outcome of Step 3a: the validated Self-Issued ID Token subject and the access token it carries.
     *
     * @param subjectDid the validated {@code sub} DID, which identifies the presenting party
     * @param accessToken the access token from the "token" claim
     */
    private record ValidatedSelfIssuedToken(String subjectDid, String accessToken) {
    }

    /**
     * Result of the complete presentation flow containing all data from Steps 3-5.
     *
     * <p>{@code getHolderDid()} is the validated Self-Issued ID Token {@code sub}, so it identifies
     * the party that presented the token.
     */
    @Getter
    public static class PresentationFlowResult {
        private final String accessToken;
        private final String holderDid;
        private final String credentialServiceUrl;
        private final List<String> scopes;
        private final PresentationResponseMessage presentationResponse;
        private final List<ValidatedPresentation> validatedPresentations;

        public PresentationFlowResult(String accessToken, String holderDid, String credentialServiceUrl,
                                     List<String> scopes, PresentationResponseMessage presentationResponse,
                                     List<ValidatedPresentation> validatedPresentations) {
            this.accessToken = accessToken;
            this.holderDid = holderDid;
            this.credentialServiceUrl = credentialServiceUrl;
            this.scopes = scopes;
            this.presentationResponse = presentationResponse;
            this.validatedPresentations = validatedPresentations;
        }
    }

    /**
     * Validated presentation containing holder DID, claims, and validated credentials.
     */
    @Getter
    public static class ValidatedPresentation {
        private final String holderDid;
        private final JWTClaimsSet presentationClaims;
        private final List<ValidatedCredential> credentials;

        public ValidatedPresentation(String holderDid, JWTClaimsSet presentationClaims,
                                    List<ValidatedCredential> credentials) {
            this.holderDid = holderDid;
            this.presentationClaims = presentationClaims;
            this.credentials = credentials;
        }
    }

    /**
     * Validated credential containing issuer DID, type, and claims.
     */
    @Getter
    public static class ValidatedCredential {
        private final String issuerDid;
        private final String credentialType;
        private final JWTClaimsSet credentialClaims;

        public ValidatedCredential(String issuerDid, String credentialType, JWTClaimsSet credentialClaims) {
            this.issuerDid = issuerDid;
            this.credentialType = credentialType;
            this.credentialClaims = credentialClaims;
        }
    }

    /**
     * Result of DSP request validation containing all necessary information for Step 4.
     */
    public static class ValidationResult {
        private final String accessToken;
        private final String holderDid;
        private final String credentialServiceUrl;

        public ValidationResult(String accessToken, String holderDid, String credentialServiceUrl) {
            this.accessToken = accessToken;
            this.holderDid = holderDid;
            this.credentialServiceUrl = credentialServiceUrl;
        }

        /**
         * Get the access token to use for querying holder's credential service.
         * @return The access token
         */
        public String getAccessToken() {
            return accessToken;
        }

        /**
         * Get the holder's DID.
         * @return The holder's DID
         */
        public String getHolderDid() {
            return holderDid;
        }

        /**
         * Get the holder's credential service endpoint URL.
         * @return The credential service URL
         */
        public String getCredentialServiceUrl() {
            return credentialServiceUrl;
        }
    }
}
