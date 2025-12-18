package it.eng.dcp.service;

import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.model.PresentationQueryMessage;
import it.eng.dcp.model.PresentationResponseMessage;
import it.eng.dcp.model.VerifiableCredential;
import it.eng.dcp.model.VerifiablePresentation;
import it.eng.dcp.repository.VerifiableCredentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service orchestrating presentation creation: fetch credentials, group by profile, build homogenous VPs and sign them.
 */
@Service
public class PresentationService {

    private final VerifiableCredentialRepository credentialRepository;
    private final VerifiablePresentationSigner vpSigner;

    @Autowired
    public PresentationService(VerifiableCredentialRepository credentialRepository, VerifiablePresentationSigner vpSigner) {
        this.credentialRepository = credentialRepository;
        this.vpSigner = vpSigner;
    }

    /**
     * Create a PresentationResponseMessage from a PresentationQueryMessage.
     * Groups fetched credentials by profileId and signs each homogenous group.
     * @param query the presentation query message containing scope and definition
     * @return the presentation response message containing signed presentations
     */
    public PresentationResponseMessage createPresentation(PresentationQueryMessage query) {
        List<String> requiredTypes = query.getScope();
        List<VerifiableCredential> fetched;
        if (requiredTypes == null || requiredTypes.isEmpty()) {
            // fetch all credentials
            fetched = credentialRepository.findAll();
        } else {
            fetched = credentialRepository.findByCredentialTypeIn(requiredTypes);
        }

        // Group by profileId (null profile grouped under empty string)
        Map<String, List<VerifiableCredential>> groups = fetched.stream()
                .collect(Collectors.groupingBy(vc -> vc.getProfileId() == null ? "" : vc.getProfileId()));

        List<Object> signedPresentations = new ArrayList<>();

        for (Map.Entry<String, List<VerifiableCredential>> e : groups.entrySet()) {
            List<VerifiableCredential> groupCreds = e.getValue();
            if (groupCreds.isEmpty()) continue;

            // Collect credential IDs for reference
            List<String> credentialIds = groupCreds.stream().map(VerifiableCredential::getId).collect(Collectors.toList());

            // Collect full credentials for embedding in VP per DCP spec Section 5.4.2
            // This allows the verifier to validate VC signatures without additional fetch
            List<Object> fullCredentials = groupCreds.stream()
                    .map(vc -> {
                        // If VC already has JWT representation, use it
                        if (vc.getJwtRepresentation() != null && !vc.getJwtRepresentation().isBlank()) {
                            return vc.getJwtRepresentation();
                        }
                        // Otherwise, if credential JSON is available, use it
                        // (For JSON-LD format or if JWT needs to be generated on-the-fly)
                        if (vc.getCredential() != null) {
                            return vc.getCredential();
                        }
                        // Fallback to credential ID
                        return vc.getId();
                    })
                    .collect(Collectors.toList());

            String profile = e.getKey().isEmpty() ? ProfileId.VC11_SL2021_JWT.toString() : e.getKey();
            VerifiablePresentation vp = VerifiablePresentation.Builder.newInstance()
                    .holderDid(groupCreds.get(0).getHolderDid())
                    .credentialIds(credentialIds)  // Keep IDs for reference/tracking
                    .credentials(fullCredentials)   // Embed full VCs per DCP spec
                    .profileId(profile)
                    .build();

            // Determine format: check presentationDefinition.format, then fall back to profileId
            String format = determineFormat(query.getPresentationDefinition(), profile);
            Object signed = vpSigner.sign(vp, format);
            signedPresentations.add(signed);
        }

        PresentationResponseMessage.Builder respBuilder = PresentationResponseMessage.Builder.newInstance();
        respBuilder.presentation(signedPresentations);
        return respBuilder.build();
    }

    /**
     * Determine the presentation format from presentationDefinition or profileId.
     * Priority: 1) format from presentationDefinition (DCP spec), 2) profileId-based format, 3) default to JWT
     *
     * Per DCP spec and Presentation Exchange spec, format can be specified in presentationDefinition:
     * - "jwt_vp" or "jwt_vc" → JWT format
     * - "ldp_vp" or "ldp_vc" → JSON-LD format
     *
     * @param presentationDefinition the presentation definition from the query (may be null)
     * @param profileId the profile identifier from credential
     * @return "jwt" or "json-ld"
     */
    private String determineFormat(Map<String, Object> presentationDefinition, String profileId) {
        // Priority 1: Extract format from presentationDefinition if present
        if (presentationDefinition != null) {
            String formatFromDef = extractFormatFromPresentationDefinition(presentationDefinition);
            if (formatFromDef != null) {
                return formatFromDef;
            }
        }

        // Priority 2: Determine from profileId
        return determineFormatFromProfile(profileId);
    }

    /**
     * Extract format from presentationDefinition per Presentation Exchange spec.
     * Looks for format field in input_descriptors or at the root level.
     *
     * @param presentationDefinition the presentation definition map
     * @return "jwt" or "json-ld" if format is found, null otherwise
     */
    @SuppressWarnings("unchecked")
    private String extractFormatFromPresentationDefinition(Map<String, Object> presentationDefinition) {
        try {
            // Check for format at root level
            Object formatObj = presentationDefinition.get("format");
            if (formatObj instanceof Map) {
                Map<String, Object> formatMap = (Map<String, Object>) formatObj;
                // Check for jwt_vp, jwt_vc (JWT formats)
                if (formatMap.containsKey("jwt_vp") || formatMap.containsKey("jwt_vc") || formatMap.containsKey("jwt")) {
                    return "jwt";
                }
                // Check for ldp_vp, ldp_vc, ldp (JSON-LD formats)
                if (formatMap.containsKey("ldp_vp") || formatMap.containsKey("ldp_vc") || formatMap.containsKey("ldp")) {
                    return "json-ld";
                }
            }

            // Check for format in input_descriptors
            Object descriptorsObj = presentationDefinition.get("input_descriptors");
            if (descriptorsObj instanceof List) {
                List<?> descriptors = (List<?>) descriptorsObj;
                for (Object descriptorObj : descriptors) {
                    if (descriptorObj instanceof Map) {
                        Map<String, Object> descriptor = (Map<String, Object>) descriptorObj;
                        Object descFormatObj = descriptor.get("format");
                        if (descFormatObj instanceof Map) {
                            Map<String, Object> descFormatMap = (Map<String, Object>) descFormatObj;
                            if (descFormatMap.containsKey("jwt_vp") || descFormatMap.containsKey("jwt_vc") || descFormatMap.containsKey("jwt")) {
                                return "jwt";
                            }
                            if (descFormatMap.containsKey("ldp_vp") || descFormatMap.containsKey("ldp_vc") || descFormatMap.containsKey("ldp")) {
                                return "json-ld";
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If parsing fails, fall back to profileId-based detection
        }

        return null; // No format specified in presentationDefinition
    }

    /**
     * Determine the presentation format based on the profileId.
     * @param profileId the profile identifier
     * @return "jwt" for JWT profiles, "json-ld" for JSON-LD profiles, defaults to "jwt"
     */
    private String determineFormatFromProfile(String profileId) {
        if (profileId == null || profileId.isEmpty()) {
            return "jwt"; // default to JWT
        }

        // Check if profile indicates JSON-LD format
        if (profileId.toUpperCase().contains("JSONLD") || profileId.toUpperCase().contains("JSON_LD")) {
            return "json-ld";
        }

        // Check if profile indicates JWT format (or default to JWT)
        return "jwt";
    }
}
