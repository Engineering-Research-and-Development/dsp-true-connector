package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.model.*;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.common.util.DidUrlConverter;
import it.eng.dcp.model.CredentialStatusRecord;
import it.eng.dcp.model.PresentationQueryMessage;
import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.dcp.model.VerifiableCredential;
import it.eng.dcp.repository.CredentialStatusRepository;
import it.eng.dcp.repository.VerifiableCredentialRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service layer for holder-side operations: presentation queries and credential reception.
 * Handles business logic for DCP holder endpoints.
 */
@Service
@Slf4j
public class HolderService {

    private final SelfIssuedIdTokenService tokenService;
    private final PresentationService presentationService;
    private final PresentationRateLimiter rateLimiter;
    private final VerifiableCredentialRepository credentialRepository;
    private final CredentialStatusRepository credentialStatusRepository;
    private final ObjectMapper mapper;
    private final CredentialIssuanceClient issuanceClient;

    @Autowired
    public HolderService(SelfIssuedIdTokenService tokenService,
                         PresentationService presentationService,
                         PresentationRateLimiter rateLimiter,
                         VerifiableCredentialRepository credentialRepository,
                         CredentialStatusRepository credentialStatusRepository,
                         ObjectMapper mapper,
                         CredentialIssuanceClient issuanceClient) {
        this.tokenService = tokenService;
        this.presentationService = presentationService;
        this.rateLimiter = rateLimiter;
        this.credentialRepository = credentialRepository;
        this.credentialStatusRepository = credentialStatusRepository;
        this.mapper = mapper;
        this.issuanceClient = issuanceClient;
    }

    /**
     * Authorize a presentation query request by validating the bearer token.
     *
     * @param bearerToken The bearer token from Authorization header (without "Bearer " prefix)
     * @return JWTClaimsSet if valid
     * @throws SecurityException if token is invalid
     */
    public JWTClaimsSet authorizePresentationQuery(String bearerToken) throws SecurityException {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new SecurityException("Bearer token is required");
        }

        log.debug("Validating bearer token for presentation query");
        return tokenService.validateToken(bearerToken);
    }

    /**
     * Check rate limit for a holder.
     *
     * @param holderDid The holder's DID
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean checkRateLimit(String holderDid) {
        boolean allowed = rateLimiter.tryConsume(holderDid);
        if (!allowed) {
            log.warn("Presentation query rate limit exceeded for holder: {}", holderDid);
        }
        return allowed;
    }

    /**
     * Create a presentation response for a query.
     *
     * @param query The presentation query message
     * @param holderDid The holder's DID for logging
     * @return PresentationResponseMessage
     */
    public PresentationResponseMessage createPresentation(PresentationQueryMessage query, String holderDid) {
        log.info("Creating presentation response for holder: {}", holderDid);
        PresentationResponseMessage response = presentationService.createPresentation(query);
        log.info("Successfully created presentation response for holder: {}", holderDid);
        return response;
    }

    /**
     * Authorize and validate an issuer's credential delivery request.
     *
     * @param bearerToken The bearer token from Authorization header (without "Bearer " prefix)
     * @return The issuer's DID from the token
     * @throws SecurityException if token is invalid or missing issuer claim
     */
    public String authorizeIssuer(String bearerToken) throws SecurityException {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new SecurityException("Bearer token is required");
        }

        log.debug("Validating issuer token for credential delivery");
        JWTClaimsSet claims = tokenService.validateToken(bearerToken);

        String issuerDid = claims.getIssuer();
        if (issuerDid == null || issuerDid.isBlank()) {
            log.error("Token validation failed: missing issuer claim");
            throw new SecurityException("Invalid token: missing issuer claim");
        }

        log.info("Authenticated issuer: {}", issuerDid);
        return issuerDid;
    }

    /**
     * Process and store issued credentials from an issuer.
     *
     * @param msg The credential message containing issued credentials
     * @param issuerDid The authenticated issuer's DID
     * @return CredentialReceptionResult containing saved count and skipped count
     */
    public CredentialReceptionResult processIssuedCredentials(CredentialMessage msg, String issuerDid) {
        if (msg.getCredentials() == null || msg.getCredentials().isEmpty()) {
            throw new IllegalArgumentException("ISSUED status requires non-empty credentials array");
        }

        log.info("Processing {} issued credentials from issuer: {}", msg.getCredentials().size(), issuerDid);

        List<VerifiableCredential> saved = new ArrayList<>();
        int skipped = 0;

        for (CredentialMessage.CredentialContainer c : msg.getCredentials()) {
            try {
                log.debug("Processing credential: type={}, format={}", c.getCredentialType(), c.getFormat());
                VerifiableCredential vc = processCredentialContainer(c, issuerDid);
                if (vc != null) {
                    saved.add(credentialRepository.save(vc));
                    log.debug("Successfully saved credential: id={}, type={}, holderDid={}",
                            vc.getId(), vc.getCredentialType(), vc.getHolderDid());
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("Failed to persist credential container with type '{}': {}",
                        c.getCredentialType(), e.getMessage(), e);
                skipped++;
            }
        }

        log.info("Credential processing complete: saved={}, skipped={}", saved.size(), skipped);

        // Persist issuance status record
        persistCredentialStatus(msg, CredentialStatus.ISSUED, saved.size());

        return new CredentialReceptionResult(saved.size(), skipped);
    }

    /**
     * Process a rejected credential message.
     *
     * @param msg The credential message with rejection
     * @return CredentialReceptionResult with zero saved/skipped
     */
    public CredentialReceptionResult processRejectedCredentials(CredentialMessage msg) {
        log.info("Received rejected credentials message from issuer {}: reason={}",
                msg.getIssuerPid(), msg.getRejectionReason());

        // Persist rejected status
        persistCredentialStatus(msg, CredentialStatus.REJECTED, 0);
        log.info("Rejected credential status recorded");

        return new CredentialReceptionResult(0, 0);
    }

    /**
     * Process a credential offer message.
     *
     * @param offer The credential offer message
     * @return true if offer is accepted
     */
    public boolean processCredentialOffer(CredentialOfferMessage offer) {
        // Validate required fields per DCP spec 6.6.1
        if (offer == null || offer.getCredentialObjects() == null || offer.getCredentialObjects().isEmpty()) {
            log.error("Invalid credential offer: offeredCredentials is null or empty");
            throw new IllegalArgumentException("offeredCredentials must be provided and non-empty");
        }

        if (offer.getIssuer() == null || offer.getIssuer().trim().isEmpty()) {
            log.error("Invalid credential offer: issuer is null or empty");
            throw new IllegalArgumentException("issuer must be provided");
        }

        log.info("Received credential offer message from issuer: {}", offer.getIssuer());
        log.info("Processing credential offer with {} offered credentials",
                offer.getCredentialObjects().size());
        log.debug("Offered credential types: {}",
                offer.getCredentialObjects().stream()
                        .map(CredentialOfferMessage.CredentialObject::getCredentialType)
                        .toList());

        // Per DCP spec 6.6.1: If credential entries are sparse (only contain an id),
        // the Credential Service MUST resolve values from the credentialsSupported list
        // returned from the Issuer Metadata API (6.7)
        if (hasSparseCredentials(offer.getCredentialObjects())) {
            log.info("Detected sparse credentials in offer - resolving from issuer metadata");
            try {
                resolveInlineCredentials(offer);
            } catch (Exception e) {
                log.error("Failed to resolve sparse credentials from issuer metadata: {}", e.getMessage());
                throw new IllegalArgumentException("Failed to resolve sparse credentials: " + e.getMessage(), e);
            }
        }

        log.info("Credential offer accepted");
        return true;
    }

    /**
     * Check if the credential list contains sparse credentials (only id, no credentialType).
     * Per DCP spec 6.6.1: "If the entries in the credentials property are sparse, i.e., only contain an id..."
     *
     * @param credentials List of credential objects from the offer
     * @return true if any credential is sparse
     */
    private boolean hasSparseCredentials(List<CredentialOfferMessage.CredentialObject> credentials) {
        return credentials.stream()
                .anyMatch(c -> c.getId() != null &&
                              (c.getCredentialType() == null || c.getCredentialType().trim().isEmpty()));
    }

    /**
     * Validate that sparse credentials can be resolved from issuer metadata.
     * Per DCP spec 6.6.1: "When processing, the Credential Service MUST resolve this string value to the respective object."
     *
     * This method validates that sparse credential IDs exist in the issuer's credentialsSupported list.
     * The actual resolution and use of full credential details happens when the holder requests credentials.
     *
     * @param offer The credential offer containing sparse credentials
     * @throws RuntimeException if metadata cannot be fetched or credentials cannot be resolved
     */
    private void resolveInlineCredentials(CredentialOfferMessage offer) {
        // Construct metadata URL: issuer base + /metadata
        String issuerBase = DidUrlConverter.convertDidToUrl(offer.getIssuer(), false);

        String metadataUrl = issuerBase.endsWith("/")
            ? issuerBase + "metadata"
            : issuerBase + "/metadata";

        log.debug("Fetching issuer metadata from: {}", metadataUrl);
        IssuerMetadata metadata;
        try {
            metadata = issuanceClient.getIssuerMetadata(metadataUrl);
        } catch (Exception e) {
            log.error("Failed to fetch issuer metadata from {}: {}", metadataUrl, e.getMessage());
            throw new RuntimeException("Cannot resolve sparse credentials - metadata fetch failed", e);
        }

        if (metadata.getCredentialsSupported() == null || metadata.getCredentialsSupported().isEmpty()) {
            log.warn("Issuer metadata contains no credentialsSupported - cannot resolve sparse credentials");
            throw new IllegalArgumentException("Issuer metadata does not contain credentialsSupported");
        }

        // Build a map of id -> full credential object from metadata
        Map<String, IssuerMetadata.CredentialObject> supportedMap = new java.util.HashMap<>();
        for (IssuerMetadata.CredentialObject supported : metadata.getCredentialsSupported()) {
            if (supported.getId() != null) {
                supportedMap.put(supported.getId(), supported);
            }
        }

        // Validate each sparse credential exists in metadata
        for (CredentialOfferMessage.CredentialObject offeredCred : offer.getCredentialObjects()) {
            if (offeredCred.getId() != null &&
                (offeredCred.getCredentialType() == null || offeredCred.getCredentialType().trim().isEmpty())) {

                log.debug("Validating sparse credential with id: {}", offeredCred.getId());
                IssuerMetadata.CredentialObject fullCred = supportedMap.get(offeredCred.getId());

                if (fullCred == null) {
                    log.error("Sparse credential id '{}' not found in issuer metadata", offeredCred.getId());
                    throw new IllegalArgumentException(
                        "Credential id '" + offeredCred.getId() + "' not found in issuer credentialsSupported");
                }

                log.info("Validated sparse credential '{}' resolves to type '{}' (bindingMethods: {}, profile: {})",
                    offeredCred.getId(),
                    fullCred.getCredentialType(),
                    fullCred.getBindingMethods(),
                    fullCred.getProfile());
            }
        }

        log.info("All sparse credentials successfully validated against issuer metadata");
    }

    /**
     * Process a single credential container and convert it to VerifiableCredential.
     *
     * @param container The credential container
     * @param issuerDid The authenticated issuer DID
     * @return VerifiableCredential or null if should be skipped
     */
    private VerifiableCredential processCredentialContainer(
            CredentialMessage.CredentialContainer container,
            String issuerDid) {

        Object payload = container.getPayload();

        // Validate that payload is not empty/null
        if (payload == null || (payload instanceof String && ((String) payload).trim().isEmpty())) {
            log.warn("Skipping credential with type '{}' - payload is empty or null. Per DCP spec 6.5.2, payload must contain a Verifiable Credential.",
                    container.getCredentialType());
            return null;
        }

        String format = container.getFormat();
        if (format == null) {
            log.warn("Skipping credential with type '{}' - format is missing", container.getCredentialType());
            return null;
        }

        VerifiableCredential.Builder cb = VerifiableCredential.Builder.newInstance();

        // Handle JWT format credentials
        if ("jwt".equalsIgnoreCase(format) || "VC1_0_JWT".equalsIgnoreCase(format) ) {
            log.debug("Processing JWT format credential: type={}", container.getCredentialType());
            if (!processJwtCredential(payload, container, cb)) {
                return null;
            }
        } else if ("json-ld".equalsIgnoreCase(format) || "ldp_vc".equalsIgnoreCase(format)) {
            log.debug("Processing JSON-LD format credential: type={}", container.getCredentialType());
            if (!processJsonLdCredential(payload, container, cb)) {
                return null;
            }
        } else {
            log.warn("Skipping credential with type '{}' - unsupported format: {}", container.getCredentialType(), format);
            return null;
        }

        // Set credentialType if not already set
        if (container.getCredentialType() != null) {
            cb.credentialType(container.getCredentialType());
        }

        // Set issuerDid
        cb.issuerDid(issuerDid);

        // Profile is now set within the format-specific processing methods
        // (processVc11JwtCredential, processVc20JwtCredential, processJsonLdCredential)

        return cb.build();
    }

    /**
     * Process a JWT format credential.
     * Handles both VC 1.1 (nested 'vc' claim) and VC 2.0 (flat structure) profiles.
     *
     * @param payload The credential payload object
     * @param container The credential container
     * @param builder The VerifiableCredential builder
     * @return true if successful, false if should be skipped
     */
    private boolean processJwtCredential(Object payload, CredentialMessage.CredentialContainer container,
                                        VerifiableCredential.Builder builder) {
        if (!(payload instanceof String jwtString)) {
            log.warn("Skipping JWT credential - payload must be a JWT string, got: {}", payload.getClass().getSimpleName());
            return false;
        }

        try {
            // Parse JWT to extract claims and credential information
            SignedJWT signedJWT = SignedJWT.parse(jwtString);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Detect VC version: VC 1.1 has nested 'vc' claim, VC 2.0 is flat
            Map<String, Object> vcClaim = claims.getJSONObjectClaim("vc");
            boolean isVc11 = (vcClaim != null);

            if (isVc11) {
                // VC 1.1 structure (vc11-sl2021/jwt profile)
                log.debug("Detected VC 1.1 structure (nested 'vc' claim)");
                return processVc11JwtCredential(vcClaim, jwtString, container, builder, claims);
            } else {
                // VC 2.0 structure (vc20-bssl/jwt profile)
                log.debug("Detected VC 2.0 structure (flat claims)");
                return processVc20JwtCredential(claims, jwtString, container, builder);
            }
        } catch (Exception e) {
            log.error("Failed to parse JWT credential: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Process VC 1.1 JWT credential (vc11-sl2021/jwt profile).
     * Structure: JWT with nested 'vc' claim containing the credential.
     *
     * @param vcClaim The nested 'vc' claim
     * @param jwtString The raw JWT string
     * @param container The credential container
     * @param builder The VerifiableCredential builder
     * @param claims The JWT claims set
     * @return true if successful, false if should be skipped
     */
    private boolean processVc11JwtCredential(Map<String, Object> vcClaim, String jwtString,
                                            CredentialMessage.CredentialContainer container,
                                            VerifiableCredential.Builder builder,
                                            JWTClaimsSet claims) {
        // Extract credential ID from vc.id
        if (vcClaim.containsKey("id")) {
            builder.id(vcClaim.get("id").toString());
        }

        // Extract holder DID from credentialSubject.id or from JWT 'sub' claim
        String holderDid = extractHolderDid(vcClaim, claims);
        if (holderDid != null) {
            builder.holderDid(holderDid);
            log.debug("Extracted holderDid: {}", holderDid);
        }

        // Extract issuance date from vc.issuanceDate
        if (vcClaim.containsKey("issuanceDate")) {
            try {
                builder.issuanceDate(Instant.parse(vcClaim.get("issuanceDate").toString()));
            } catch (Exception e) {
                log.debug("Could not parse issuanceDate: {}", e.getMessage());
            }
        }

        // Extract expiration date from vc.expirationDate
        if (vcClaim.containsKey("expirationDate") && vcClaim.get("expirationDate") != null) {
            try {
                builder.expirationDate(Instant.parse(vcClaim.get("expirationDate").toString()));
            } catch (Exception e) {
                log.debug("Could not parse expirationDate: {}", e.getMessage());
            }
        }

        // Extract credentialStatus if present (StatusList2021)
        if (vcClaim.containsKey("credentialStatus")) {
            JsonNode statusNode = mapper.convertValue(vcClaim.get("credentialStatus"), JsonNode.class);
            builder.credentialStatus(statusNode);
        }

        // Store credential as JsonNode
        JsonNode jwtNode = mapper.createObjectNode()
                .put("type", container.getCredentialType())
                .put("format", "jwt")
                .put("jwt", jwtString);
        builder.credential(jwtNode);

        // Store JWT representation for later use
        builder.jwtRepresentation(jwtString);

        // Set profile to VC 1.1
        builder.profileId(ProfileId.VC11_SL2021_JWT);

        log.debug("VC 1.1 JWT credential stored: type={}, profile={}",
                  container.getCredentialType(), ProfileId.VC11_SL2021_JWT.getSpecAlias());
        return true;
    }

    /**
     * Process VC 2.0 JWT credential (vc20-bssl/jwt profile).
     * Structure: Flat JWT claims, no nested 'vc' claim.
     *
     * @param claims The JWT claims set
     * @param jwtString The raw JWT string
     * @param container The credential container
     * @param builder The VerifiableCredential builder
     * @return true if successful, false if should be skipped
     */
    private boolean processVc20JwtCredential(JWTClaimsSet claims, String jwtString,
                                            CredentialMessage.CredentialContainer container,
                                            VerifiableCredential.Builder builder) {
        // VC 2.0 uses JWT claims directly (flat structure)

        // Extract credential ID from 'jti' claim or generate one
        String credId = claims.getJWTID();
        if (credId != null) {
            builder.id(credId);
        }

        // Extract holder DID from 'sub' claim
        String holderDid = claims.getSubject();
        if (holderDid != null) {
            builder.holderDid(holderDid);
            log.debug("Extracted holderDid from 'sub': {}", holderDid);
        }

        // Extract credentialSubject from direct claim
        try {
            Map<String, Object> credentialSubject = claims.getJSONObjectClaim("credentialSubject");
            if (credentialSubject != null && credentialSubject.containsKey("id")) {
                // Override with credentialSubject.id if present
                String subjectId = credentialSubject.get("id").toString();
                builder.holderDid(subjectId);
                log.debug("Extracted holderDid from credentialSubject: {}", subjectId);
            }
        } catch (java.text.ParseException e) {
            log.debug("Could not parse credentialSubject claim: {}", e.getMessage());
        }

        // Extract issuance date from 'validFrom' (VC 2.0) or 'nbf'/'iat'
        Object validFrom = claims.getClaim("validFrom");
        if (validFrom != null) {
            try {
                builder.issuanceDate(Instant.parse(validFrom.toString()));
            } catch (Exception e) {
                log.debug("Could not parse validFrom: {}", e.getMessage());
            }
        } else if (claims.getNotBeforeTime() != null) {
            builder.issuanceDate(claims.getNotBeforeTime().toInstant());
        } else if (claims.getIssueTime() != null) {
            builder.issuanceDate(claims.getIssueTime().toInstant());
        }

        // Extract expiration date from 'validUntil' (VC 2.0) or 'exp'
        Object validUntil = claims.getClaim("validUntil");
        if (validUntil != null) {
            try {
                builder.expirationDate(Instant.parse(validUntil.toString()));
            } catch (Exception e) {
                log.debug("Could not parse validUntil: {}", e.getMessage());
            }
        } else if (claims.getExpirationTime() != null) {
            builder.expirationDate(claims.getExpirationTime().toInstant());
        }

        // Extract credentialStatus if present (BitstringStatusList for VC 2.0)
        Object credentialStatus = claims.getClaim("credentialStatus");
        if (credentialStatus != null) {
            JsonNode statusNode = mapper.convertValue(credentialStatus, JsonNode.class);
            builder.credentialStatus(statusNode);
        }

        // Store credential as JsonNode
        JsonNode jwtNode = mapper.createObjectNode()
                .put("type", container.getCredentialType())
                .put("format", "jwt")
                .put("jwt", jwtString);
        builder.credential(jwtNode);

        // Store JWT representation for later use
        builder.jwtRepresentation(jwtString);

        // Set profile to VC 2.0
        builder.profileId(ProfileId.VC20_BSSL_JWT);

        log.debug("VC 2.0 JWT credential stored: type={}, profile={}",
                  container.getCredentialType(), ProfileId.VC20_BSSL_JWT.getSpecAlias());
        return true;
    }

    /**
     * Extract holder DID from VC 1.1 credential claims.
     * Tries credentialSubject.id first, then falls back to JWT 'sub' claim.
     *
     * @param vcClaim The VC claim map
     * @param claims The JWT claims set
     * @return The holder DID or null if not found
     */
    private String extractHolderDid(Map<String, Object> vcClaim, JWTClaimsSet claims) {
        // Try credentialSubject.id first
        if (vcClaim.containsKey("credentialSubject")) {
            Object credentialSubject = vcClaim.get("credentialSubject");
            if (credentialSubject instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subjectMap = (Map<String, Object>) credentialSubject;
                if (subjectMap.containsKey("id")) {
                    return subjectMap.get("id").toString();
                }
            }
        }

        // Fallback to JWT 'sub' claim
        return claims.getSubject();
    }

    /**
     * Process a JSON-LD format credential.
     *
     * NOTE: JSON-LD format is NOT part of the official DCP profiles (vc11-sl2021/jwt, vc20-bssl/jwt).
     * Both official profiles use JWT format exclusively.
     * This method serves as a placeholder for potential custom profile extensions.
     *
     * @param payload The credential payload object
     * @param container The credential container (unused but kept for consistency)
     * @param builder The VerifiableCredential builder
     * @return true if successful, false if should be skipped
     */
    private boolean processJsonLdCredential(Object payload, CredentialMessage.CredentialContainer container,
                                           VerifiableCredential.Builder builder) {
        log.warn("JSON-LD format is not part of official DCP profiles. Official profiles are: vc11-sl2021/jwt, vc20-bssl/jwt");

        // TODO: Future implementation for custom JSON-LD profile support
        // This would require:
        // 1. Custom profile definition outside official DCP spec
        // 2. JSON-LD signature verification
        // 3. Profile resolver extension

        JsonNode payloadNode = null;

        if (payload instanceof JsonNode) {
            payloadNode = (JsonNode) payload;
        } else if (payload instanceof Map) {
            payloadNode = mapper.convertValue(payload, JsonNode.class);
        } else if (payload instanceof String) {
            try {
                payloadNode = mapper.readTree((String) payload);
            } catch (Exception e) {
                log.warn("Skipping credential - failed to parse JSON-LD payload: {}", e.getMessage());
                return false;
            }
        }

        if (payloadNode == null || !payloadNode.isObject()) {
            log.warn("Skipping credential - JSON-LD payload must be a JSON object (Verifiable Credential)");
            return false;
        }

        if (payloadNode.has("id")) {
            builder.id(payloadNode.get("id").asText());
        }
        builder.credential(payloadNode);

        // Extract holder DID from credentialSubject.id
        if (payloadNode.has("credentialSubject")) {
            JsonNode credentialSubject = payloadNode.get("credentialSubject");
            if (credentialSubject.has("id")) {
                String holderDid = credentialSubject.get("id").asText();
                builder.holderDid(holderDid);
                log.debug("Extracted holderDid from credentialSubject: {}", holderDid);
            }
        }

        // Attempt to set issuance/expiration if present
        try {
            if (payloadNode.has("issuanceDate")) {
                builder.issuanceDate(Instant.parse(payloadNode.get("issuanceDate").asText()));
            }
            if (payloadNode.has("expirationDate") && !payloadNode.get("expirationDate").isNull()) {
                builder.expirationDate(Instant.parse(payloadNode.get("expirationDate").asText()));
            }
        } catch (Exception ignored) {
        }

        // Set default profile to VC20_BSSL_JWT (since no official JSON-LD profile exists)
        builder.profileId(ProfileId.VC20_BSSL_JWT);

        log.warn("Stored JSON-LD credential with default profile (not officially supported)");
        return true;
    }


    /**
     * Persist credential status record.
     *
     * @param msg The credential message
     * @param status The credential status
     * @param savedCount The number of saved credentials
     */
    private void persistCredentialStatus(CredentialMessage msg, CredentialStatus status, int savedCount) {
        try {
            String requestId = msg.getRequestId();
            if (requestId == null || requestId.isBlank()) requestId = msg.getIssuerPid();
            if (requestId == null || requestId.isBlank()) requestId = "req-" + UUID.randomUUID();

            log.debug("Persisting credential status record: requestId={}, status={}, savedCount={}",
                    requestId, status, savedCount);

            CredentialStatusRecord.Builder builder = CredentialStatusRecord.Builder.newInstance()
                    .requestId(requestId)
                    .issuerPid(msg.getIssuerPid())
                    .holderPid(msg.getHolderPid())
                    .status(status)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now());

            if (status == CredentialStatus.ISSUED) {
                builder.savedCount(savedCount);
            } else if (status == CredentialStatus.REJECTED) {
                builder.rejectionReason(msg.getRejectionReason());
            }

            credentialStatusRepository.save(builder.build());
            log.info("Credential status record saved: requestId={}, status={}", requestId, status);
        } catch (Exception e) {
            log.error("Failed to persist credential status record: {}", e.getMessage(), e);
        }
    }

    /**
     * Result object for credential reception operations.
     */
    public static class CredentialReceptionResult {
        private final int savedCount;
        private final int skippedCount;

        public CredentialReceptionResult(int savedCount, int skippedCount) {
            this.savedCount = savedCount;
            this.skippedCount = skippedCount;
        }

        public int getSavedCount() {
            return savedCount;
        }

        public int getSkippedCount() {
            return skippedCount;
        }

        public boolean hasSkipped() {
            return skippedCount > 0;
        }

        public boolean isEmpty() {
            return savedCount == 0 && skippedCount > 0;
        }
    }
}
