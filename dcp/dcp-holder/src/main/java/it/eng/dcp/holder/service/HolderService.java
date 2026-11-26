package it.eng.dcp.holder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.audit.DcpAuditEventType;
import it.eng.dcp.common.model.*;
import it.eng.dcp.common.service.IssuerTrustService;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import it.eng.dcp.common.util.DidUrlConverter;
import it.eng.dcp.holder.exception.CredentialOfferForbiddenException;
import it.eng.dcp.holder.model.CredentialStatusRecord;
import it.eng.dcp.holder.model.VerifiableCredential;
import it.eng.dcp.holder.repository.CredentialStatusRepository;
import it.eng.dcp.holder.repository.VerifiableCredentialRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.text.ParseException;
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

    private static final String HOLDER_SOURCE = "holder";

    private static final String TOKEN_CLAIM = "token";

    private final SelfIssuedIdTokenService tokenService;
    private final PresentationService presentationService;
    private final PresentationRateLimiter rateLimiter;
    private final VerifiableCredentialRepository credentialRepository;
    private final CredentialStatusRepository credentialStatusRepository;
    private final ObjectMapper mapper;
    private final CredentialIssuanceClient issuanceClient;
    private final IssuerTrustService issuerTrustService;
    private final DcpAuditEventPublisher auditPublisher;

    @Autowired
    public HolderService(@Qualifier("selfIssuedIdTokenService") SelfIssuedIdTokenService tokenService,
                         PresentationService presentationService,
                         PresentationRateLimiter rateLimiter,
                         VerifiableCredentialRepository credentialRepository,
                         CredentialStatusRepository credentialStatusRepository,
                         ObjectMapper mapper,
                         CredentialIssuanceClient issuanceClient,
                         IssuerTrustService issuerTrustService,
                         DcpAuditEventPublisher auditPublisher) {
        this.tokenService = tokenService;
        this.presentationService = presentationService;
        this.rateLimiter = rateLimiter;
        this.credentialRepository = credentialRepository;
        this.credentialStatusRepository = credentialStatusRepository;
        this.mapper = mapper;
        this.issuanceClient = issuanceClient;
        this.issuerTrustService = issuerTrustService;
        this.auditPublisher = auditPublisher;
    }

    /**
     * Authorize a presentation query request (DCP v1.0.1 §5.1 step 7, §5.3.1).
     *
     * <p>Validates the bearer token as the Verifier's Self-Issued ID Token ({@code aud} = this participant's
     * DID, signature against the Verifier's DID document, {@code jti} replay check), then validates the access
     * token in its {@code token} claim: issued by this Credential Service's holder and bound to that Verifier
     * ({@code aud} = the Self-Issued ID Token {@code sub}).
     *
     * @param bearerToken The bearer token from Authorization header (without "Bearer " prefix)
     * @return the Verifier DID, the holder DID and the validated access token claims
     * @throws SecurityException if either token is missing or invalid
     */
    public PresentationQueryAuthorization authorizePresentationQuery(String bearerToken) throws SecurityException {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw presentationQueryRejected("Bearer token is required");
        }

        log.debug("Validating the Verifier's Self-Issued ID Token for presentation query");
        JWTClaimsSet idTokenClaims = tokenService.validateToken(bearerToken);
        String verifierDid = idTokenClaims.getSubject();

        String accessToken;
        try {
            accessToken = idTokenClaims.getStringClaim(TOKEN_CLAIM);
        } catch (ParseException e) {
            throw presentationQueryRejected("Self-Issued ID Token 'token' claim is not a string");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw presentationQueryRejected("Self-Issued ID Token carries no access token in its 'token' claim");
        }

        JWTClaimsSet accessTokenClaims = tokenService.validateAccessToken(accessToken, verifierDid);
        String holderDid = accessTokenClaims.getSubject();
        auditPublisher.publishEvent(
                DcpAuditEventType.PRESENTATION_QUERY_RECEIVED,
                "Presentation query received from verifier: " + verifierDid,
                HOLDER_SOURCE,
                holderDid, null, null, null,
                Map.of("holderDid", holderDid, "verifierDid", verifierDid));
        return new PresentationQueryAuthorization(verifierDid, holderDid, accessTokenClaims);
    }

    /**
     * Audits a rejected presentation query and creates the exception to throw.
     *
     * @param reason the rejection reason
     * @return the exception to throw
     */
    private SecurityException presentationQueryRejected(String reason) {
        auditPublisher.publishEvent(
                DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                "Presentation query authorization failed: " + reason,
                HOLDER_SOURCE,
                null, null, null, null,
                Map.of("reason", reason));
        return new SecurityException(reason);
    }

    /**
     * Check the presentation query rate limit for a caller.
     *
     * @param callerDid The DID of the querying party (the Verifier)
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean checkRateLimit(String callerDid) {
        boolean allowed = rateLimiter.tryConsume(callerDid);
        if (!allowed) {
            log.warn("Presentation query rate limit exceeded for caller: {}", callerDid);
        }
        return allowed;
    }

    /**
     * Create a presentation response for an authorized query.
     *
     * <p>Per DCP v1.0.1 §5.3.1 and §5.4.1.2, the authorized scopes come only from the validated access
     * token, and only credentials of the holder that owns this Credential Service are returned.
     *
     * @param query The presentation query message containing requested scopes
     * @param authorization The result of {@link #authorizePresentationQuery(String)}
     * @return PresentationResponseMessage with only authorized credentials
     */
    public PresentationResponseMessage createPresentation(PresentationQueryMessage query,
                                                          PresentationQueryAuthorization authorization) {
        String holderDid = authorization.holderDid();
        log.info("Creating presentation response for verifier {} from holder {}",
                authorization.verifierDid(), holderDid);
        PresentationResponseMessage response = presentationService.createPresentation(
                query, holderDid, authorization.accessTokenClaims());
        List<String> scopes = query.getScope() != null ? query.getScope() : List.of();
        auditPublisher.publishEvent(
                DcpAuditEventType.PRESENTATION_CREATED,
                "Verifiable Presentation created for holder: " + holderDid,
                HOLDER_SOURCE,
                holderDid, null, scopes, null,
                Map.of("scopes", scopes, "verifierDid", authorization.verifierDid()));
        return response;
    }

    /**
     * Authorize and validate an issuer's credential delivery request.
     *
     * <p>Validates the issuer's Self-Issued ID Token and the Holder-issued access token carried in its
     * {@code token} claim. The nested access token must be addressed to the authenticated issuer DID.
     *
     * @param bearerToken The bearer token from Authorization header (without "Bearer " prefix)
     * @return The issuer's DID from the token
     * @throws SecurityException if either token is invalid or missing
     */
    public String authorizeIssuer(String bearerToken) throws SecurityException {
        if (bearerToken == null || bearerToken.isBlank()) {
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Credential delivery authorization failed: bearer token is missing",
                    HOLDER_SOURCE,
                    null, null, null, null,
                    Map.of("reason", "Bearer token is required"));
            throw new SecurityException("Bearer token is required");
        }

        log.debug("Validating issuer token for credential delivery");
        JWTClaimsSet claims = tokenService.validateToken(bearerToken);

        String issuerDid = claims.getIssuer();
        if (issuerDid == null || issuerDid.isBlank()) {
            log.error("Token validation failed: missing issuer claim");
            auditPublisher.publishEvent(
                    DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                    "Credential delivery authorization failed: missing issuer claim",
                    HOLDER_SOURCE,
                    null, null, null, null,
                    Map.of("reason", "Missing issuer claim"));
            throw new SecurityException("Invalid token: missing issuer claim");
        }

        String accessToken;
        try {
            accessToken = claims.getStringClaim(TOKEN_CLAIM);
        } catch (ParseException e) {
            throw credentialDeliveryRejected("Self-Issued ID Token 'token' claim is not a string", issuerDid, e);
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw credentialDeliveryRejected(
                    "Self-Issued ID Token carries no access token in its 'token' claim", issuerDid, null);
        }

        try {
            tokenService.validateAccessToken(accessToken, issuerDid);
        } catch (SecurityException e) {
            throw credentialDeliveryRejected("Holder access token is invalid", issuerDid, e);
        }

        log.info("Authenticated issuer: {}", issuerDid);
        auditPublisher.publishEvent(
                DcpAuditEventType.CREDENTIAL_MESSAGE_RECEIVED,
                "Credential delivery message received from authenticated issuer: " + issuerDid,
                HOLDER_SOURCE,
                null, issuerDid, null, null,
                Map.of("issuerDid", issuerDid));
        return issuerDid;
    }

    private SecurityException credentialDeliveryRejected(String reason, String issuerDid, Exception cause) {
        auditPublisher.publishEvent(
                DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                "Credential delivery authorization failed: " + reason,
                HOLDER_SOURCE,
                null, issuerDid, null, null,
                Map.of("reason", reason));
        return new SecurityException(reason, cause);
    }

    /**
     * Authorizes a credential offer (DCP v1.0.1 §6.6).
     *
     * <p>The bearer token must be a valid Self-Issued ID Token addressed to this participant (validated by
     * {@link SelfIssuedIdTokenService#validateToken(String)}). Its {@code sub} must equal the offer's
     * {@code issuer}, and that issuer must be trusted for at least one credential type
     * ({@code dcp.trusted-issuers.*}).
     *
     * @param bearerToken the bearer token from the Authorization header (without "Bearer " prefix)
     * @param offerIssuer the {@code issuer} of the received offer
     * @return the authenticated and trusted issuer DID (the token {@code sub})
     * @throws SecurityException if the token is missing or invalid
     * @throws IllegalArgumentException if the offer has no issuer
     * @throws CredentialOfferForbiddenException if {@code sub} is not the offer issuer or the issuer is not trusted
     */
    public String authorizeCredentialOffer(String bearerToken, String offerIssuer) {
        if (bearerToken == null || bearerToken.isBlank()) {
            publishOfferRejected("bearer token is missing", null);
            throw new SecurityException("Bearer token is required");
        }

        log.debug("Validating issuer token for credential offer");
        JWTClaimsSet claims = tokenService.validateToken(bearerToken);
        String subject = claims.getSubject();

        if (offerIssuer == null || offerIssuer.isBlank()) {
            throw new IllegalArgumentException("issuer must be provided");
        }
        if (!DidUrlConverter.compareDids(subject, offerIssuer)) {
            log.warn("Credential offer rejected: token subject {} is not the offer issuer {}", subject, offerIssuer);
            publishOfferRejected("token subject does not match the offer issuer", subject);
            throw new CredentialOfferForbiddenException("Token subject does not match the offer issuer");
        }
        if (!issuerTrustService.isTrustedIssuer(subject)) {
            log.warn("Credential offer rejected: issuer {} is not trusted", subject);
            publishOfferRejected("offer issuer is not trusted", subject);
            throw new CredentialOfferForbiddenException("Offer issuer is not trusted");
        }

        log.info("Authenticated credential offer issuer: {}", subject);
        return subject;
    }

    /**
     * Publishes the audit event of a rejected credential offer.
     *
     * @param reason the rejection reason, without token content
     * @param issuerDid the authenticated token subject, or {@code null} when not known
     */
    private void publishOfferRejected(String reason, String issuerDid) {
        auditPublisher.publishEvent(
                DcpAuditEventType.TOKEN_VALIDATION_FAILED,
                "Credential offer authorization failed: " + reason,
                HOLDER_SOURCE,
                null, issuerDid, null, null,
                Map.of("reason", reason));
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
                    if (!issuerTrustService.isTrusted(vc.getCredentialType(), vc.getIssuerDid())) {
                        log.warn("============ Rejecting credential of type '{}' — issuer '{}' is not trusted. " +
                                        "Configure dcp.trusted-issuers.{}=<issuerDid> to trust this issuer.",
                                vc.getCredentialType(), vc.getIssuerDid(), vc.getCredentialType());
                        auditPublisher.publishEvent(
                                DcpAuditEventType.CREDENTIAL_UNTRUSTED_ISSUER,
                                "Credential from untrusted issuer skipped: type=" + vc.getCredentialType()
                                        + ", issuer=" + vc.getIssuerDid(),
                                HOLDER_SOURCE,
                                vc.getHolderDid(), vc.getIssuerDid(),
                                List.of(vc.getCredentialType()),
                                msg.getIssuerPid(),
                                Map.of("credentialType", vc.getCredentialType(),
                                       "issuerDid", vc.getIssuerDid()));
                        skipped++;
                        continue;
                    }
                    saved.add(credentialRepository.save(vc));
                    log.debug("Successfully saved credential: id={}, type={}, holderDid={}",
                            vc.getId(), vc.getCredentialType(), vc.getHolderDid());
                    auditPublisher.publishEvent(
                            DcpAuditEventType.CREDENTIAL_SAVED,
                            "Credential saved: type=" + vc.getCredentialType()
                                    + ", holder=" + vc.getHolderDid(),
                            HOLDER_SOURCE,
                            vc.getHolderDid(), vc.getIssuerDid(),
                            List.of(vc.getCredentialType()),
                            msg.getIssuerPid(),
                            Map.of("credentialType", vc.getCredentialType()));
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

        auditPublisher.publishEvent(
                DcpAuditEventType.CREDENTIALS_PROCESSED,
                "Credential batch processed: saved=" + saved.size() + ", skipped=" + skipped
                        + ", issuer=" + issuerDid,
                HOLDER_SOURCE,
                null, issuerDid,
                null,
                msg.getIssuerPid(),
                Map.of("savedCount", saved.size(),
                       "skippedCount", skipped,
                       "totalCount", saved.size() + skipped,
                       "issuerDid", issuerDid));

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

        auditPublisher.publishEvent(
                DcpAuditEventType.CREDENTIAL_REJECTED_BY_ISSUER,
                "Credential rejected by issuer: issuerPid=" + msg.getIssuerPid()
                        + (msg.getRejectionReason() != null ? ", reason=" + msg.getRejectionReason() : ", no reason given"),
                HOLDER_SOURCE,
                null, msg.getIssuerPid(),
                null,
                msg.getIssuerPid(),
                Map.of("issuerPid", msg.getIssuerPid() != null ? msg.getIssuerPid() : "",
                       "rejectionReason", msg.getRejectionReason() != null ? msg.getRejectionReason() : ""));

        return new CredentialReceptionResult(0, 0);
    }

    /**
     * Process a credential offer message.
     *
     * <p>Call {@link #authorizeCredentialOffer(String, String)} first. Sparse credential entries are resolved
     * from the metadata of the offer's issuer, which must be trusted.
     *
     * @param offer The credential offer message
     * @return true if offer is accepted
     * @throws IllegalArgumentException if the offer is invalid or its sparse credentials cannot be resolved
     * @throws CredentialOfferForbiddenException if the offer has sparse credentials and its issuer is not trusted
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
        boolean sparseResolved = false;
        if (hasSparseCredentials(offer.getCredentialObjects())) {
            log.info("Detected sparse credentials in offer - resolving from issuer metadata");
            try {
                resolveInlineCredentials(offer);
                sparseResolved = true;
            } catch (CredentialOfferForbiddenException e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to resolve sparse credentials from issuer metadata: {}", e.getMessage());
                throw new IllegalArgumentException("Failed to resolve sparse credentials: " + e.getMessage(), e);
            }
        }

        log.info("Credential offer accepted");
        List<String> offeredTypes = offer.getCredentialObjects().stream()
                .map(CredentialOfferMessage.CredentialObject::getCredentialType)
                .filter(t -> t != null && !t.isBlank())
                .toList();
        auditPublisher.publishEvent(
                DcpAuditEventType.CREDENTIAL_OFFER_RECEIVED,
                "Credential offer received from issuer: " + offer.getIssuer(),
                HOLDER_SOURCE,
                null, offer.getIssuer(),
                offeredTypes,
                null,
                Map.of("issuerDid", offer.getIssuer(),
                       "offeredCredentialCount", offer.getCredentialObjects().size(),
                       "sparseCredentialsResolved", sparseResolved));
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
     * <p>This method validates that sparse credential IDs exist in the issuer's credentialsSupported list.
     * The actual resolution and use of full credential details happens when the holder requests credentials.
     *
     * <p>The metadata is fetched only from the {@code IssuerService} endpoint advertised in the DID document of a
     * trusted issuer (DCP v1.0.1 §6.2); no URL is derived from the offer itself.
     *
     * @param offer The credential offer containing sparse credentials
     * @throws CredentialOfferForbiddenException if the offer issuer is not trusted
     * @throws RuntimeException if metadata cannot be fetched or credentials cannot be resolved
     */
    private void resolveInlineCredentials(CredentialOfferMessage offer) {
        String offerIssuer = offer.getIssuer();
        if (!issuerTrustService.isTrustedIssuer(offerIssuer)) {
            throw new CredentialOfferForbiddenException("Offer issuer is not trusted");
        }

        log.debug("Fetching issuer metadata of {} from its advertised IssuerService endpoint", offerIssuer);
        IssuerMetadata metadata;
        try {
            metadata = issuanceClient.getIssuerMetadataOf(offerIssuer);
        } catch (Exception e) {
            log.error("Failed to fetch issuer metadata of {}: {}", offerIssuer, e.getMessage());
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
