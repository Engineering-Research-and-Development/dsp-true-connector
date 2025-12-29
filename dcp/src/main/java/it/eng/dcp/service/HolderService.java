package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialOfferMessage;
import it.eng.dcp.common.model.CredentialStatus;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.core.ProfileResolver;
import it.eng.dcp.model.*;
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
    private final ProfileResolver profileResolver;
    private final ObjectMapper mapper;

    @Autowired
    public HolderService(SelfIssuedIdTokenService tokenService,
                        PresentationService presentationService,
                        PresentationRateLimiter rateLimiter,
                        VerifiableCredentialRepository credentialRepository,
                        CredentialStatusRepository credentialStatusRepository,
                        ProfileResolver profileResolver,
                        ObjectMapper mapper) {
        this.tokenService = tokenService;
        this.presentationService = presentationService;
        this.rateLimiter = rateLimiter;
        this.credentialRepository = credentialRepository;
        this.credentialStatusRepository = credentialStatusRepository;
        this.profileResolver = profileResolver;
        this.mapper = mapper;
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
        if (offer == null || offer.getCredentialObjects() == null || offer.getCredentialObjects().isEmpty()) {
            log.error("Invalid credential offer: offeredCredentials is null or empty");
            throw new IllegalArgumentException("offeredCredentials must be provided and non-empty");
        }

        log.info("Received credential offer message");
        log.info("Processing credential offer with {} offered credentials",
                offer.getCredentialObjects().size());
        log.debug("Offered credential types: {}",
                offer.getCredentialObjects().stream()
                        .map(CredentialOfferMessage.CredentialObject::getCredentialType)
                        .toList());

        log.info("Credential offer accepted");
        return true;
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

        // Determine and set profileId using ProfileResolver
        setProfileId(cb, format);

        return cb.build();
    }

    /**
     * Process a JWT format credential.
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
            com.nimbusds.jwt.SignedJWT signedJWT = com.nimbusds.jwt.SignedJWT.parse(jwtString);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Extract vc claim which contains the Verifiable Credential
            Map<String, Object> vcClaim = claims.getJSONObjectClaim("vc");
            if (vcClaim == null) {
                log.warn("Skipping JWT credential - missing 'vc' claim");
                return false;
            }

            // Extract credential ID from vc.id
            if (vcClaim.containsKey("id")) {
                builder.id(vcClaim.get("id").toString());
            }

            // Extract holder DID from credentialSubject.id
            if (vcClaim.containsKey("credentialSubject")) {
                Object credentialSubject = vcClaim.get("credentialSubject");
                if (credentialSubject instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> subjectMap = (Map<String, Object>) credentialSubject;
                    if (subjectMap.containsKey("id")) {
                        String holderDid = subjectMap.get("id").toString();
                        builder.holderDid(holderDid);
                        log.debug("Extracted holderDid from credentialSubject: {}", holderDid);
                    }
                }
            }

            // Extract issuance date
            if (vcClaim.containsKey("issuanceDate")) {
                try {
                    builder.issuanceDate(Instant.parse(vcClaim.get("issuanceDate").toString()));
                } catch (Exception e) {
                    log.debug("Could not parse issuanceDate: {}", e.getMessage());
                }
            }

            // Extract expiration date
            if (vcClaim.containsKey("expirationDate") && vcClaim.get("expirationDate") != null) {
                try {
                    builder.expirationDate(Instant.parse(vcClaim.get("expirationDate").toString()));
                } catch (Exception e) {
                    log.debug("Could not parse expirationDate: {}", e.getMessage());
                }
            }

            // Store credential as JsonNode
            JsonNode jwtNode = mapper.createObjectNode()
                    .put("type", container.getCredentialType())
                    .put("format", "jwt")
                    .put("jwt", jwtString);
            builder.credential(jwtNode);

            // Store JWT representation for later use (e.g., in presentations)
            builder.jwtRepresentation(jwtString);

            log.debug("JWT credential stored: type={}", container.getCredentialType());
            return true;
        } catch (Exception e) {
            log.error("Failed to parse JWT credential: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Process a JSON-LD format credential.
     *
     * @param payload The credential payload object
     * @param container The credential container (unused but kept for consistency)
     * @param builder The VerifiableCredential builder
     * @return true if successful, false if should be skipped
     */
    private boolean processJsonLdCredential(Object payload, CredentialMessage.CredentialContainer container,
                                           VerifiableCredential.Builder builder) {
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

        return true;
    }

    /**
     * Set the profile ID for a credential using the ProfileResolver.
     *
     * @param builder The VerifiableCredential builder
     * @param format The credential format (jwt, json-ld, etc.)
     */
    private void setProfileId(VerifiableCredential.Builder builder, String format) {
        Map<String, Object> attributes = new java.util.HashMap<>();

        // Check if credential has credentialStatus (StatusList2021)
        //TODO this cannot be done using calling build() before building the final object
//        if (builder.build().getCredentialStatus() != null) {
//            attributes.put("statusList", true);
//        }

        ProfileId profileId = profileResolver.resolve(format, attributes);
        if (profileId != null) {
            builder.profileId(profileId.toString());
        } else {
            builder.profileId(ProfileId.VC11_SL2021_JWT.toString());
            log.debug("ProfileResolver returned null for format '{}', using default profile: {}",
                    format, ProfileId.VC11_SL2021_JWT);
        }
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
