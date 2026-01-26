package it.eng.dcp.verifier.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.PresentationQueryMessage;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.service.did.HttpDidResolverService;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.common.util.DidUrlConverter;
import it.eng.dcp.common.util.DidUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
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
 *   <li>Step 4: Query holder's credential service using the access token</li>
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
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final SelfIssuedIdTokenService tokenService;
    private final HttpDidResolverService didResolverService;
    private final SimpleOkHttpRestClient httpClient;
    private final ObjectMapper objectMapper;
    private final BaseDidDocumentConfiguration verifierConfig;

    @Getter
    private final DidDocumentConfig verifierDidConfig;

    /**
     * Constructor with explicit verifier configuration injection.
     *
     * @param tokenService The token service configured with verifier's DID (for aud validation)
     * @param didResolverService Service for resolving DIDs to DID documents
     * @param httpClient HTTP client for making REST calls
     * @param objectMapper JSON mapper for request/response serialization
     * @param verifierConfig The verifier configuration (explicitly qualified)
     */
    @Autowired
    public VerifierService(
            @Qualifier("verifierTokenService") SelfIssuedIdTokenService tokenService,
            HttpDidResolverService didResolverService,
            SimpleOkHttpRestClient httpClient,
            ObjectMapper objectMapper,
            @Qualifier("verifier") BaseDidDocumentConfiguration verifierConfig) {
        this.tokenService = tokenService;
        this.didResolverService = didResolverService;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.verifierConfig = verifierConfig;
        this.verifierDidConfig = verifierConfig.getDidDocumentConfig();

        log.info("VerifierService initialized with verifier DID: {}", verifierDidConfig.getDid());
    }


    /**
     * STEP 3: Validate self-issued ID token and extract access token.
     *
     * <p>This implements Step 3 of the DCP flow:
     * <ul>
     *   <li>Validates the self-issued ID token (iss==sub, aud==verifier DID, signature)</li>
     *   <li>Extracts the "token" claim containing the access token</li>
     * </ul>
     *
     * @param bearerToken The self-issued ID token from Authorization header (without "Bearer " prefix)
     * @return The access token extracted from the "token" claim
     * @throws SecurityException if token is invalid or "token" claim is missing
     */
    private String validateAndExtractAccessToken(String bearerToken) throws SecurityException {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new SecurityException("Bearer token is required");
        }

        log.debug("STEP 3: Validating self-issued ID token for verifier DID: {}", verifierDidConfig.getDid());

        // Validate the self-issued ID token
        // This checks: iss==sub, aud==verifier DID, signature, expiry
        JWTClaimsSet claims = tokenService.validateToken(bearerToken);

        // Extract the access token from "token" claim
        String accessToken;
        try {
            accessToken = claims.getStringClaim("token");
        } catch (java.text.ParseException e) {
            log.error("Failed to parse 'token' claim from self-issued ID token", e);
            throw new SecurityException("Invalid 'token' claim in self-issued ID token");
        }

        if (accessToken == null || accessToken.isBlank()) {
            log.error("Self-issued ID token missing 'token' claim");
            throw new SecurityException("Self-issued ID token must contain 'token' claim with access token");
        }

        log.debug("Successfully extracted access token from self-issued ID token");
        return accessToken;
    }

    /**
     * STEP 3 (continued): Resolve holder's DID to find their credential service endpoint.
     *
     * <p>Fetches the holder's DID document and extracts the CredentialService endpoint URL.
     *
     * @param holderDid The holder's DID (from iss claim of self-issued ID token)
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
                return endpoint;
            }
        }

        throw new DidResolutionException(
            "Holder DID document missing CredentialService endpoint: " + holderDid);
    }

    /**
     * STEP 4: Query holder's credential service for verifiable presentations.
     *
     * <p>Sends a POST request to {holder-credential-service}/presentations/query
     * with the access token in Authorization header.
     *
     * @param credentialServiceUrl The holder's credential service base URL
     * @param accessToken The access token (from "token" claim of self-issued ID token)
     * @param presentationQueryMessage The query message with scopes
     * @return The presentation response from holder
     * @throws IOException if HTTP request fails
     * @throws SecurityException if access token is rejected by holder
     */
    private PresentationResponseMessage queryHolderPresentations(
            String credentialServiceUrl,
            String accessToken,
            PresentationQueryMessage presentationQueryMessage) throws IOException, SecurityException {

        String queryUrl = credentialServiceUrl + PRESENTATIONS_QUERY_PATH;
        log.debug("STEP 4: Querying holder credential service: {}", queryUrl);

        // Prepare headers with access token
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");

        // Serialize query message to JSON
        String jsonBody = objectMapper.writeValueAsString(presentationQueryMessage);
        RequestBody requestBody = RequestBody.create(jsonBody, JSON);

        log.debug("Sending presentation query with access token to holder");

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
     *   <li>Validates outer JWT signature using holder's DID public key</li>
     *   <li>Verifies holder DID matches expected holder</li>
     *   <li>Extracts embedded verifiable credentials from 'vp.verifiableCredential' claim</li>
     *   <li>Validates each credential JWT signature using issuer's DID public key</li>
     *   <li>Verifies credential claims (iss, sub, exp, etc.)</li>
     * </ul>
     *
     * @param presentationResponse The presentation response from holder
     * @param expectedHolderDid The expected holder DID (from access token)
     * @return List of validated presentations with their credentials
     * @throws SecurityException if validation fails
     */
    private List<ValidatedPresentation> validatePresentationResponse(
            PresentationResponseMessage presentationResponse,
            String expectedHolderDid) throws SecurityException {

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

                // Verify holder DID matches expected holder from access token
                if (!DidUtils.compareIgnoringFragment(DidUrlConverter.normalizeDid(holderDid),
                        DidUrlConverter.normalizeDid(expectedHolderDid))) {
                    throw new SecurityException(String.format(
                        "Presentation holder DID mismatch: expected=%s, actual=%s",
                        expectedHolderDid, holderDid));
                }

                log.debug("Presentation holder DID verified: {}", holderDid);

                // Verify presentation JWT signature using holder's public key
                String kid = presentationSignedJwt.getHeader().getKeyID();
                validateJwtSignature(presentationSignedJwt, holderDid, kid, "Presentation");

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
     * Validate an embedded verifiable credential JWT.
     *
     * <p>Validates:
     * <ul>
     *   <li>JWT signature using issuer's DID public key</li>
     *   <li>Issuer DID presence (iss claim)</li>
     *   <li>Subject matches holder DID</li>
     *   <li>Expiration date (exp claim)</li>
     *   <li>Issuance date (iat claim)</li>
     * </ul>
     *
     * @param credentialJwt The credential JWT string
     * @param expectedHolderDid The expected holder DID (subject of credential)
     * @return ValidatedCredential containing credential claims and metadata
     * @throws SecurityException if validation fails
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

            // Verify credential JWT signature using issuer's public key
            String kid = credentialSignedJwt.getHeader().getKeyID();
            validateJwtSignature(credentialSignedJwt, issuerDid, kid, "Credential");

            log.debug("✓ Credential JWT signature verified for issuer: {}", issuerDid);

            // Verify subject matches holder DID
            String subject = credentialClaims.getSubject();
            if (subject != null && !DidUtils.compareIgnoringFragment(
                    DidUrlConverter.normalizeDid(subject),
                    DidUrlConverter.normalizeDid(expectedHolderDid))) {
                // For VC 1.1, subject might be in nested vc.credentialSubject.id
                // Check nested vc claim
                Object vcClaimObj = credentialClaims.getClaim("vc");
                if (vcClaimObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> vcClaim = (Map<String, Object>) vcClaimObj;
                    Object credSubjectObj = vcClaim.get("credentialSubject");
                    if (credSubjectObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> credSubject = (Map<String, Object>) credSubjectObj;
                        String nestedSubject = (String) credSubject.get("id");
                        if (nestedSubject != null && !DidUtils.compareIgnoringFragment(
                                DidUrlConverter.normalizeDid(nestedSubject),
                                DidUrlConverter.normalizeDid(expectedHolderDid))) {
                            throw new SecurityException(String.format(
                                "Credential subject mismatch: expected=%s, actual=%s",
                                expectedHolderDid, nestedSubject));
                        }
                    }
                }
            }

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
     * Validate JWT signature using DID-based key resolution.
     *
     * @param signedJwt The signed JWT to validate
     * @param issuerDid The DID of the JWT issuer
     * @param kid The key ID from JWT header
     * @param contextName Context name for logging (e.g., "Presentation", "Credential")
     * @throws SecurityException if signature validation fails
     */
    private void validateJwtSignature(SignedJWT signedJwt, String issuerDid, String kid, String contextName)
            throws SecurityException {

        try {
            // Resolve public key for the issuer DID
            JWK jwk = didResolverService.resolvePublicKey(issuerDid, kid, "assertionMethod");
            if (jwk == null) {
                // Fallback to capabilityInvocation if assertionMethod not found
                jwk = didResolverService.resolvePublicKey(issuerDid, kid, "capabilityInvocation");
            }

            if (jwk == null) {
                throw new SecurityException(String.format(
                    "%s signature validation failed: No public key found for DID=%s, kid=%s",
                    contextName, issuerDid, kid));
            }

            // Verify signature using resolved public key
            ECKey ecPub = (ECKey) jwk;
            JWSVerifier verifier = new ECDSAVerifier(ecPub.toECPublicKey());
            if (!signedJwt.verify(verifier)) {
                throw new SecurityException(String.format(
                    "%s signature validation failed: Invalid signature for DID=%s",
                    contextName, issuerDid));
            }

            log.debug("✓ {} JWT signature verified for DID: {}", contextName, issuerDid);

        } catch (DidResolutionException e) {
            throw new SecurityException(String.format(
                "%s signature validation failed: Cannot resolve DID=%s - %s",
                contextName, issuerDid, e.getMessage()));
        } catch (Exception e) {
            throw new SecurityException(String.format(
                "%s signature validation failed: %s",
                contextName, e.getMessage()));
        }
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
     *   <li>Parse access token to extract scopes (Step 3b)</li>
     *   <li>Extract holder DID (Step 3b)</li>
     *   <li>Resolve holder's credential service endpoint (Step 3c)</li>
     *   <li>Build PresentationQueryMessage with scopes from access token (Step 4)</li>
     *   <li>Query holder's credential service with access token (Step 4)</li>
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
        log.info("Verifier DID: {}", verifierDidConfig.getDid());
        log.info("═══════════════════════════════════════════════════════════");

        // ═════════════════════════════════════════════════════════════
        // STEP 3a: Validate self-issued ID token and extract access token
        // ═════════════════════════════════════════════════════════════
        String accessToken;
        try {
            log.info("STEP 3a: Validating self-issued ID token...");
            accessToken = validateAndExtractAccessToken(selfIssuedIdToken);
            log.info("STEP 3a: ✓ Self-issued ID token valid, access token extracted");
        } catch (SecurityException e) {
            log.error("STEP 3a FAILED: {}", e.getMessage());
            throw new SecurityException("Step 3a failed - " + e.getMessage());
        }

        // ═════════════════════════════════════════════════════════════
        // STEP 3b: Parse access token to extract scopes
        // ═════════════════════════════════════════════════════════════
        List<String> scopes;
        String holderDid;
        try {
            log.info("STEP 3b: Parsing access token to extract scopes and holder DID...");

            // Parse access token JWT
            SignedJWT accessTokenJwt = SignedJWT.parse(accessToken);
            JWTClaimsSet accessTokenClaims = accessTokenJwt.getJWTClaimsSet();

            // Extract holder DID from iss claim
            holderDid = accessTokenClaims.getIssuer();
            if (holderDid == null || holderDid.isBlank()) {
                throw new SecurityException("Access token missing 'iss' claim (holder DID)");
            }
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

        } catch (Exception e) {
            log.error("STEP 3b FAILED: Failed to parse access token - {}", e.getMessage());
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

            // Query holder with access token
            PresentationResponseMessage presentationResponse = queryHolderPresentations(
                credentialServiceUrl,
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
                validatedPresentations = validatePresentationResponse(presentationResponse, holderDid);
                log.info("STEP 5: ✓ Validated {} presentation(s) with credentials", validatedPresentations.size());

                // Log summary of validated credentials
                for (ValidatedPresentation vp : validatedPresentations) {
                    log.debug("  Holder: {}", vp.getHolderDid());
                    for (ValidatedCredential vc : vp.getCredentials()) {
                        log.debug("    ✓ Credential: type={}, issuer={}", vc.getCredentialType(), vc.getIssuerDid());
                    }
                }

            } catch (SecurityException e) {
                log.error("STEP 5 FAILED: Presentation validation failed - {}", e.getMessage());
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
     * Result of the complete presentation flow containing all data from Steps 3-5.
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
