package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.dcp.core.ProfileResolver;
import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.dcp.model.ValidationError;
import it.eng.dcp.model.ValidationReport;
import it.eng.dcp.model.VerifiableCredential;
import it.eng.dcp.model.VerifiablePresentation;
import it.eng.tools.event.AuditEvent;
import it.eng.tools.event.AuditEventType;
import it.eng.tools.service.AuditEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class PresentationValidationServiceImpl implements PresentationValidationService {

    private final ProfileResolver profileResolver;
    private final IssuerTrustService issuerTrustService;
    private final SchemaRegistryService schemaRegistryService;

    private final RevocationService revocationService;

    private final AuditEventPublisher publisher;

    @Autowired
    public PresentationValidationServiceImpl(ProfileResolver profileResolver,
                                             IssuerTrustService issuerTrustService,
                                             SchemaRegistryService schemaRegistryService,
                                             RevocationService revocationService,
                                             AuditEventPublisher publisher) {
        this.profileResolver = profileResolver;
        this.issuerTrustService = issuerTrustService;
        this.schemaRegistryService = schemaRegistryService;
        // optional collaborators may be null in some test contexts; keep them nullable
        this.revocationService = revocationService;
        this.publisher = publisher;
    }

    @Override
    public ValidationReport validate(PresentationResponseMessage rsp, List<String> requiredCredentialTypes, TokenContext tokenCtx) {
        ValidationReport report = ValidationReport.Builder.newInstance().build();
        if (rsp == null) {
            report.addError(new ValidationError("RSP_NULL", "PresentationResponseMessage is null", ValidationError.Severity.ERROR));
            return report;
        }

        List<Object> presentations = rsp.getPresentation();
        if (presentations == null || presentations.isEmpty()) {
            report.addError(new ValidationError("NO_PRESENTATIONS", "No presentations provided", ValidationError.Severity.ERROR));
            return report;
        }

        // For each presentation (assume either VerifiablePresentation object or raw Json)
        for (Object pObj : presentations) {
            VerifiablePresentation vp;
            if (pObj instanceof VerifiablePresentation) {
                vp = (VerifiablePresentation) pObj;
            } else if (pObj instanceof JsonNode node) {
                // try to map minimal fields using pattern variable
                vp = jsonToVp(node);
            } else if (pObj instanceof Map) {
                // conservative handling: try to extract fields
                @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) pObj;
                vp = mapToVp(map);
            } else {
                report.addError(new ValidationError("VP_UNRECOGNIZED", "Unrecognized presentation type", ValidationError.Severity.ERROR));
                continue;
            }

            // profile homogeneity: profileId present
            String profileId = vp.getProfileId();
            if (profileId == null || profileId.isBlank()) {
                report.addError(new ValidationError("PROFILE_MISSING", "Presentation missing profileId", ValidationError.Severity.ERROR));
                continue;
            }

            // ensure credentialIds non-empty
            List<String> credIds = vp.getCredentialIds();
            if (credIds == null || credIds.isEmpty()) {
                report.addError(new ValidationError("VP_NO_CREDENTIALS", "Presentation contains no credentials", ValidationError.Severity.ERROR));
                continue;
            }

            // For each credential id we should fetch the stored VerifiableCredential - but in Phase 2.1 we'll assume the
            // presentation includes raw credentials in the presentation JSON. We'll attempt to parse any embedded credentials
            // from vp.presentation if present. Otherwise, mark as warning (external fetch required).

            List<VerifiableCredential> creds = extractCredentialsFromVp(vp);
            if (creds.isEmpty()) {
                report.addError(new ValidationError("CRED_MISSING_PAYLOAD", "No credential payloads found in presentation; repository lookup required", ValidationError.Severity.WARNING));
                // can't validate further for this VP
                continue;
            }

            // Enforce homogeneity by resolving profile for each credential via profileResolver (format + attributes)
            boolean mixed = false;
            for (VerifiableCredential vc : creds) {
                String format = determineFormat(vc);
                Map<String, Object> attrs = new HashMap<>();
                // look for statusList in the credential JSON (best effort)
                try { if (vc.getCredential() != null && vc.getCredential().has("credentialStatus")) attrs.put("statusList", vc.getCredential().get("credentialStatus")); } catch (Exception ignored) {}
                var resolved = profileResolver.resolve(format, attrs);
                if (resolved == null) {
                    report.addError(new ValidationError("PROFILE_UNKNOWN", "Could not resolve profile for credential " + vc.getId(), ValidationError.Severity.ERROR));
                    mixed = true;
                    break;
                }
                if (!resolved.name().equals(profileId)) {
                    mixed = true;
                    break;
                }
            }
            if (mixed) {
                report.addError(new ValidationError("PROFILE_MIXED", "Credentials inside presentation have mixed profiles", ValidationError.Severity.ERROR));
                continue;
            }

            // Now validate each credential
            for (VerifiableCredential vc : creds) {
                // issuer trust
                String issuer = extractIssuer(vc);
                if (issuer == null) {
                    report.addError(new ValidationError("VC_ISSUER_MISSING", "Credential issuer not found for " + vc.getId(), ValidationError.Severity.ERROR));
                    continue;
                }
                if (!issuerTrustService.isTrusted(vc.getCredentialType(), issuer)) {
                    report.addError(new ValidationError("ISSUER_UNTRUSTED", "Issuer not trusted: " + issuer + " for type " + vc.getCredentialType(), ValidationError.Severity.ERROR));
                    if (publisher != null) {
                        publisher.publishEvent(AuditEvent.Builder.newInstance()
                                .eventType(AuditEventType.PRESENTATION_INVALID)
                                .description("Untrusted issuer detected during presentation validation")
                                .details(Map.of(
                                        "errorCode", "ISSUER_UNTRUSTED",
                                        "credentialId", vc.getId(),
                                        "credentialType", vc.getCredentialType(),
                                        "issuerDid", issuer,
                                        "trustedIssuers", issuerTrustService.getTrustedIssuers(vc.getCredentialType())
                                ))
                                .build());
                    }
                }

                // dates
                Instant now = Instant.now();
                if (vc.getIssuanceDate() != null && vc.getIssuanceDate().isAfter(now)) {
                    report.addError(new ValidationError("VC_NOT_YET_VALID", "Credential not yet valid: " + vc.getId(), ValidationError.Severity.ERROR));
                }
                if (vc.getExpirationDate() != null && vc.getExpirationDate().isBefore(now)) {
                    report.addError(new ValidationError("VC_EXPIRED", "Credential expired: " + vc.getId(), ValidationError.Severity.ERROR));
                }

                // revocation
                try {
                    if (revocationService != null && revocationService.isRevoked(vc)) {
                        report.addError(new ValidationError("VC_REVOKED", "Credential revoked: " + vc.getId(), ValidationError.Severity.ERROR));
                        // publish credential revoked event
                        if (publisher != null) {
                            publisher.publishEvent(AuditEventType.CREDENTIAL_REVOKED,
                                    "Credential revoked detected during presentation validation",
                                    Map.of("credentialId", vc.getId(), "credentialType", vc.getCredentialType()));
                        }
                    }
                } catch (Exception e) {
                    report.addError(new ValidationError("REVOCATION_CHECK_FAILED", "Failed to check revocation for " + vc.getId() + ": " + e.getMessage(), ValidationError.Severity.WARNING));
                }

                // schema check - best effort using credentialType as schema id
                if (vc.getCredential() != null && vc.getCredential().has("@context")) {
                    // attempt to validate schema id if present
                    if (vc.getCredential().has("credentialSchema") && vc.getCredential().get("credentialSchema").has("id")) {
                        String schemaId = vc.getCredential().get("credentialSchema").get("id").asText(null);
                        if (schemaId != null && !schemaRegistryService.exists(schemaId)) {
                            report.addError(new ValidationError("VC_SCHEMA_NOT_FOUND", "Schema not found: " + schemaId + " for cred " + vc.getId(), ValidationError.Severity.ERROR));
                        }
                    }
                }

                // if all good, mark type accepted
                report.addAccepted(vc.getCredentialType());
            }
        }

        // Finally, ensure requiredCredentialTypes are satisfied by accepted types
        if (requiredCredentialTypes != null && !requiredCredentialTypes.isEmpty()) {
            for (String req : requiredCredentialTypes) {
                if (!report.getAcceptedCredentialTypes().contains(req)) {
                    report.addError(new ValidationError("REQUIREMENT_UNMET", "Required credential type not satisfied: " + req, ValidationError.Severity.ERROR));
                }
            }
        }

        // If validation produced any errors, publish a PRESENTATION_INVALID audit event with details
        if (!report.getErrors().isEmpty()) {
            if (publisher != null) {
                publisher.publishEvent(AuditEvent.Builder.newInstance()
                        .eventType(AuditEventType.PRESENTATION_INVALID)
                        .description("Presentation validation produced errors")
                        .details(Map.of("errors", report.getErrors(), "accepted", report.getAcceptedCredentialTypes()))
                        .build());
            }
        }

        return report;
    }

    private VerifiablePresentation jsonToVp(JsonNode node) {
        VerifiablePresentation.Builder b = VerifiablePresentation.Builder.newInstance();
        if (node.has("id")) b.id(node.get("id").asText());

        // ensure holderDid is present (use provided or default test DID)
        if (node.has("holderDid")) {
            b.holderDid(node.get("holderDid").asText());
        } else if (node.has("holder")) {
            b.holderDid(node.get("holder").asText());
        } else {
            b.holderDid("did:example:holder");
        }

        if (node.has("profileId")) b.profileId(node.get("profileId").asText());

        if (node.has("credentialIds") && node.get("credentialIds").isArray()) {
            List<String> ids = new ArrayList<>();
            node.get("credentialIds").forEach(n -> ids.add(n.asText()));
            b.credentialIds(ids);
        }

        // Handle verifiableCredential array
        if (node.has("verifiableCredential") && node.get("verifiableCredential").isArray()) {
            List<Object> credentials = new ArrayList<>();
            node.get("verifiableCredential").forEach(credNode -> {
                // Convert JsonNode to appropriate format (Map for processing)
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    Object credObj = mapper.treeToValue(credNode, Object.class);
                    credentials.add(credObj);
                } catch (Exception e) {
                    // Skip malformed credential
                }
            });
            b.credentials(credentials);

            // Generate credential IDs if not provided
            if (!node.has("credentialIds")) {
                List<String> credIds = new ArrayList<>();
                for (int i = 0; i < credentials.size(); i++) {
                    credIds.add("urn:uuid:credential-" + i);
                }
                b.credentialIds(credIds);
            }
        }

        if (node.has("proof")) b.proof(node.get("proof"));
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private VerifiablePresentation mapToVp(Map<String, Object> map) {
        VerifiablePresentation.Builder b = VerifiablePresentation.Builder.newInstance();

        // Extract standard VP fields
        if (map.containsKey("id")) {
            b.id(String.valueOf(map.get("id")));
        }

        // holderDid can be in multiple places: holderDid, holder, or derived from parent JWT's iss/sub
        if (map.containsKey("holderDid")) {
            b.holderDid(String.valueOf(map.get("holderDid")));
        } else if (map.containsKey("holder")) {
            b.holderDid(String.valueOf(map.get("holder")));
        } else {
            // Default - will be overridden by outer JWT's iss claim in the provider
            b.holderDid("did:example:holder");
        }

        // ProfileId
        if (map.containsKey("profileId")) {
            b.profileId(String.valueOf(map.get("profileId")));
        }

        // Extract credentialIds if present (list of credential IDs)
        if (map.containsKey("credentialIds") && map.get("credentialIds") instanceof List) {
            b.credentialIds((List<String>) map.get("credentialIds"));
        }

        // Handle verifiableCredential array (can contain JWT or JSON formatted credentials)
        if (map.containsKey("verifiableCredential") && map.get("verifiableCredential") instanceof List) {
            List<Object> credentials = (List<Object>) map.get("verifiableCredential");
            b.credentials(credentials);

            // Also extract credential IDs if not already set
            // Generate IDs from the credentials for the credentialIds list
            if (!map.containsKey("credentialIds") || ((List<?>) map.get("credentialIds")).isEmpty()) {
                List<String> credIds = new ArrayList<>();
                for (int i = 0; i < credentials.size(); i++) {
                    credIds.add("urn:uuid:credential-" + i);
                }
                b.credentialIds(credIds);
            }
        }

        return b.build();
    }

    private List<VerifiableCredential> extractCredentialsFromVp(VerifiablePresentation vp) {
        List<VerifiableCredential> out = new ArrayList<>();

        // First, try to extract from credentials list (handles JWT, JSON, and JsonNode formats)
        if (vp.getCredentials() != null && !vp.getCredentials().isEmpty()) {
            for (Object credObj : vp.getCredentials()) {
                try {
                    // Handle JsonNode format (from tests or JSON deserialization)
                    if (credObj instanceof JsonNode credNode) {
                        VerifiableCredential.Builder cb = VerifiableCredential.Builder.newInstance();
                        if (credNode.has("id")) cb.id(credNode.get("id").asText());
                        if (credNode.has("type") && credNode.get("type").isArray()) {
                            cb.credentialType(credNode.get("type").get(0).asText());
                        } else if (credNode.has("type")) {
                            cb.credentialType(credNode.get("type").asText());
                        }
                        if (vp.getHolderDid() != null) cb.holderDid(vp.getHolderDid());
                        if (credNode.has("issuer")) {
                            JsonNode issuerNode = credNode.get("issuer");
                            if (issuerNode.isTextual()) {
                                // Store issuer for later extraction
                            }
                        }
                        if (credNode.has("issuanceDate")) cb.issuanceDate(Instant.parse(credNode.get("issuanceDate").asText()));
                        if (credNode.has("expirationDate")) cb.expirationDate(Instant.parse(credNode.get("expirationDate").asText()));
                        cb.credential(credNode);
                        out.add(cb.build());
                    }
                    // Handle Map format
                    else if (credObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> credMap = (Map<String, Object>) credObj;

                        // Handle JWT format: {type: "MembershipCredential", format: "jwt", jwt: "eyJ..."}
                        if (credMap.containsKey("format") && "jwt".equals(credMap.get("format")) && credMap.containsKey("jwt")) {
                            String jwtString = (String) credMap.get("jwt");
                            String credType = credMap.containsKey("type") ? String.valueOf(credMap.get("type")) : "VerifiableCredential";

                            // Parse JWT to extract claims (we'll use nimbus-jose-jwt library)
                            try {
                                com.nimbusds.jwt.SignedJWT signedJWT = com.nimbusds.jwt.SignedJWT.parse(jwtString);
                                var claims = signedJWT.getJWTClaimsSet();

                                VerifiableCredential.Builder cb = VerifiableCredential.Builder.newInstance();
                                cb.credentialType(credType);

                                // Extract standard fields from JWT claims
                                if (claims.getJWTID() != null) cb.id(claims.getJWTID());
                                if (vp.getHolderDid() != null) cb.holderDid(vp.getHolderDid());
                                if (claims.getIssueTime() != null) cb.issuanceDate(claims.getIssueTime().toInstant());
                                if (claims.getExpirationTime() != null) cb.expirationDate(claims.getExpirationTime().toInstant());

                                // Store the JWT claims as credential payload with format metadata
                                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                JsonNode credNode = mapper.readTree(claims.toString());

                                // Wrap in object with format metadata so determineFormat() can detect it
                                com.fasterxml.jackson.databind.node.ObjectNode wrappedCred = mapper.createObjectNode();
                                wrappedCred.put("format", "jwt");
                                wrappedCred.set("claims", credNode);

                                cb.credential(wrappedCred);

                                out.add(cb.build());
                            } catch (Exception e) {
                                // Failed to parse JWT, skip this credential
                            }
                        }
                        // Handle JSON format: full credential object as map
                        else {
                            VerifiableCredential.Builder cb = VerifiableCredential.Builder.newInstance();
                            if (credMap.containsKey("id")) cb.id(String.valueOf(credMap.get("id")));
                            if (credMap.containsKey("type")) {
                                Object typeObj = credMap.get("type");
                                if (typeObj instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<String> types = (List<String>) typeObj;
                                    if (!types.isEmpty()) cb.credentialType(types.get(0));
                                } else {
                                    cb.credentialType(String.valueOf(typeObj));
                                }
                            }
                            if (vp.getHolderDid() != null) cb.holderDid(vp.getHolderDid());

                            // Convert map to JsonNode
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            JsonNode credNode = mapper.valueToTree(credMap);

                            if (credNode.has("issuanceDate")) cb.issuanceDate(Instant.parse(credNode.get("issuanceDate").asText()));
                            if (credNode.has("expirationDate")) cb.expirationDate(Instant.parse(credNode.get("expirationDate").asText()));
                            cb.credential(credNode);
                            out.add(cb.build());
                        }
                    } else if (credObj instanceof String) {
                        // Raw JWT string
                        String jwtString = (String) credObj;
                        try {
                            com.nimbusds.jwt.SignedJWT signedJWT = com.nimbusds.jwt.SignedJWT.parse(jwtString);
                            var claims = signedJWT.getJWTClaimsSet();

                            VerifiableCredential.Builder cb = VerifiableCredential.Builder.newInstance();
                            cb.credentialType("VerifiableCredential");

                            if (claims.getJWTID() != null) cb.id(claims.getJWTID());
                            if (vp.getHolderDid() != null) cb.holderDid(vp.getHolderDid());
                            if (claims.getIssueTime() != null) cb.issuanceDate(claims.getIssueTime().toInstant());
                            if (claims.getExpirationTime() != null) cb.expirationDate(claims.getExpirationTime().toInstant());

                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            JsonNode credNode = mapper.readTree(claims.toString());
                            cb.credential(credNode);

                            out.add(cb.build());
                        } catch (Exception e) {
                            // Failed to parse JWT, skip
                        }
                    }
                } catch (Exception e) {
                    // skip malformed credential
                }
            }
        }
        return out;
    }

    private String determineFormat(VerifiableCredential vc) {
        try {
            if (vc.getCredential() != null) {
                // Check for explicit format field (added when parsing JWT credentials)
                if (vc.getCredential().has("format")) {
                    return vc.getCredential().get("format").asText();
                }
                // Check for proof (indicates JSON-LD)
                if (vc.getCredential().has("proof")) {
                    return "json-ld";
                }
            }
        } catch (Exception ignored) {}
        return "jwt"; // default guess
    }

    private String extractIssuer(VerifiableCredential vc) {
        try {
            if (vc.getCredential() != null) {
                JsonNode credNode = vc.getCredential();

                // Check if wrapped JWT format (has format field and claims)
                if (credNode.has("format") && "jwt".equals(credNode.get("format").asText()) && credNode.has("claims")) {
                    credNode = credNode.get("claims");
                }

                // Now extract issuer from the credential node
                if (credNode.has("issuer") || credNode.has("iss")) {
                    JsonNode issuer = credNode.has("issuer") ? credNode.get("issuer") : credNode.get("iss");
                    if (issuer.isTextual()) return issuer.asText();
                    if (issuer.has("id")) return issuer.get("id").asText();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

}
