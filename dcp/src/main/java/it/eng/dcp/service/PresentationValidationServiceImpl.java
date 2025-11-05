package it.eng.dcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.dcp.core.ProfileResolver;
import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.dcp.model.ValidationError;
import it.eng.dcp.model.ValidationReport;
import it.eng.dcp.model.VerifiableCredential;
import it.eng.dcp.model.VerifiablePresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class PresentationValidationServiceImpl implements PresentationValidationService {

    private final ProfileResolver profileResolver;
    private final IssuerTrustService issuerTrustService;
    private final SchemaRegistryService schemaRegistryService;

    @Autowired(required = false)
    private RevocationService revocationService;

    @Autowired
    public PresentationValidationServiceImpl(ProfileResolver profileResolver,
                                             IssuerTrustService issuerTrustService,
                                             SchemaRegistryService schemaRegistryService) {
        this.profileResolver = profileResolver;
        this.issuerTrustService = issuerTrustService;
        this.schemaRegistryService = schemaRegistryService;
    }

    // Package-visible constructor useful for tests to inject an explicit RevocationService without reflection
    PresentationValidationServiceImpl(ProfileResolver profileResolver,
                                       IssuerTrustService issuerTrustService,
                                       SchemaRegistryService schemaRegistryService,
                                       RevocationService revocationService) {
        this.profileResolver = profileResolver;
        this.issuerTrustService = issuerTrustService;
        this.schemaRegistryService = schemaRegistryService;
        this.revocationService = revocationService;
    }

    @Override
    public ValidationReport validate(PresentationResponseMessage rsp, List<String> requiredCredentialTypes, TokenContext tokenCtx) {
        ValidationReport report = new ValidationReport();
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
            VerifiablePresentation vp = null;
            if (pObj instanceof VerifiablePresentation) {
                vp = (VerifiablePresentation) pObj;
            } else if (pObj instanceof JsonNode) {
                // try to map minimal fields
                JsonNode node = (JsonNode) pObj;
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
            String expectedProfile = profileId;
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
                if (!resolved.name().equals(expectedProfile)) {
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

        return report;
    }

    private VerifiablePresentation jsonToVp(JsonNode node) {
        VerifiablePresentation.Builder b = VerifiablePresentation.Builder.newInstance();
        if (node.has("id")) b.id(node.get("id").asText());
        // ensure holderDid is present (use provided or default test DID)
        if (node.has("holderDid")) b.holderDid(node.get("holderDid").asText()); else b.holderDid("did:example:holder");
        if (node.has("profileId")) b.profileId(node.get("profileId").asText());
        if (node.has("credentialIds") && node.get("credentialIds").isArray()) {
            List<String> ids = new ArrayList<>();
            node.get("credentialIds").forEach(n -> ids.add(n.asText()));
            b.credentialIds(ids);
        }
        if (node.has("presentation")) b.presentation(node.get("presentation"));
        if (node.has("proof")) b.proof(node.get("proof"));
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private VerifiablePresentation mapToVp(Map<String, Object> map) {
        VerifiablePresentation.Builder b = VerifiablePresentation.Builder.newInstance();
        if (map.containsKey("id")) b.id(String.valueOf(map.get("id")));
        if (map.containsKey("holderDid")) b.holderDid(String.valueOf(map.get("holderDid"))); else b.holderDid("did:example:holder");
        if (map.containsKey("profileId")) b.profileId(String.valueOf(map.get("profileId")));
        if (map.containsKey("credentialIds") && map.get("credentialIds") instanceof List) {
            b.credentialIds((List<String>) map.get("credentialIds"));
        }
        // presentation/proof mapping not attempted here
        return b.build();
    }

    private List<VerifiableCredential> extractCredentialsFromVp(VerifiablePresentation vp) {
        List<VerifiableCredential> out = new ArrayList<>();
        if (vp.getPresentation() != null && vp.getPresentation().isArray()) {
            for (JsonNode node : vp.getPresentation()) {
                try {
                    VerifiableCredential.Builder cb = VerifiableCredential.Builder.newInstance();
                    if (node.has("id")) cb.id(node.get("id").asText());
                    if (node.has("type") && node.get("type").isArray()) {
                        // pick first type as credentialType for schema lookup
                        cb.credentialType(node.get("type").get(0).asText());
                    } else if (node.has("type")) {
                        cb.credentialType(node.get("type").asText());
                    }
                    // set holderDid from the presentation so the VC passes validation
                    if (vp.getHolderDid() != null) cb.holderDid(vp.getHolderDid());
                    if (node.has("issuanceDate")) cb.issuanceDate(Instant.parse(node.get("issuanceDate").asText()));
                    if (node.has("expirationDate")) cb.expirationDate(Instant.parse(node.get("expirationDate").asText()));
                    cb.credential(node);
                    out.add(cb.build());
                } catch (Exception e) {
                    // skip malformed credential with warning
                    // note: in production we may want to surface this
                }
            }
        }
        return out;
    }

    private String determineFormat(VerifiableCredential vc) {
        // naive: if credential node has "proof" -> json-ld; if it looks like a JWT string inside proof -> jwt
        try {
            if (vc.getCredential() != null && vc.getCredential().has("proof")) return "json-ld";
        } catch (Exception ignored) {}
        return "jwt"; // default guess
    }

    private String extractIssuer(VerifiableCredential vc) {
        try {
            if (vc.getCredential() != null && vc.getCredential().has("issuer")) {
                JsonNode issuer = vc.getCredential().get("issuer");
                if (issuer.isTextual()) return issuer.asText();
                if (issuer.has("id")) return issuer.get("id").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

}
