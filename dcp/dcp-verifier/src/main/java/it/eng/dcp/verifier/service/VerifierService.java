package it.eng.dcp.verifier.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.exception.DidResolutionException;
import it.eng.dcp.common.model.DidDocument;
import it.eng.dcp.common.model.PresentationResponseMessage;
import it.eng.dcp.common.model.ServiceEntry;
import it.eng.dcp.common.service.did.HttpDidResolverService;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * @param tokenService The token service for validation (uses @Primary holder config internally)
     * @param didResolverService Service for resolving DIDs to DID documents
     * @param httpClient HTTP client for making REST calls
     * @param objectMapper JSON mapper for request/response serialization
     * @param verifierConfig The verifier configuration (explicitly qualified)
     */
    @Autowired
    public VerifierService(
            SelfIssuedIdTokenService tokenService,
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
     * @param presentationQueryMessage The query message body (type, scope, etc.)
     * @return The presentation response from holder
     * @throws IOException if HTTP request fails
     * @throws SecurityException if access token is rejected by holder
     */
    private PresentationResponseMessage queryHolderPresentations(
            String credentialServiceUrl,
            String accessToken,
            Object presentationQueryMessage) throws IOException, SecurityException {

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
     * MAIN FLOW: Complete DCP presentation authentication flow (Steps 3 & 4).
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
     *   <li>Validate presentation response (Step 4)</li>
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
            } else if (scopeClaim instanceof String) {
                // Handle space-separated scopes (OAuth2 style)
                String scopeString = (String) scopeClaim;
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

            // Build query message with scopes from access token
            Map<String, Object> queryMessage = new HashMap<>();
            queryMessage.put("@type", "PresentationQueryMessage");
            queryMessage.put("scope", scopes);

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

            log.info("STEP 4: ✓ Presentation response received from holder");
            log.info("═══════════════════════════════════════════════════════════");
            log.info("✓✓✓ MAIN FLOW COMPLETE - All steps passed successfully");
            log.info("═══════════════════════════════════════════════════════════");

            // Return complete result
            return new PresentationFlowResult(
                accessToken,
                holderDid,
                credentialServiceUrl,
                scopes,
                presentationResponse
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
     * Result of the complete presentation flow containing all data from Steps 3 & 4.
     */
    public static class PresentationFlowResult {
        private final String accessToken;
        private final String holderDid;
        private final String credentialServiceUrl;
        private final List<String> scopes;
        private final PresentationResponseMessage presentationResponse;

        public PresentationFlowResult(String accessToken, String holderDid, String credentialServiceUrl,
                                     List<String> scopes, PresentationResponseMessage presentationResponse) {
            this.accessToken = accessToken;
            this.holderDid = holderDid;
            this.credentialServiceUrl = credentialServiceUrl;
            this.scopes = scopes;
            this.presentationResponse = presentationResponse;
        }

        /**
         * Get the access token used for querying holder's credential service.
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
         * Get the holder's credential service URL.
         * @return The credential service URL
         */
        public String getCredentialServiceUrl() {
            return credentialServiceUrl;
        }

        /**
         * Get the scopes extracted from the access token.
         * @return List of scope strings (credential types)
         */
        public List<String> getScopes() {
            return scopes;
        }

        /**
         * Get the presentation response from the holder.
         * @return The presentation response
         */
        public PresentationResponseMessage getPresentationResponse() {
            return presentationResponse;
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
