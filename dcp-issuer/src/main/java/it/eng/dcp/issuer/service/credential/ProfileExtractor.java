package it.eng.dcp.issuer.service.credential;

import it.eng.dcp.common.model.CredentialGenerationContext;
import it.eng.dcp.common.model.ProfileId;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Utility class for extracting profile information from generation context.
 *
 * <p>Extracts the DCP profile (VC11_SL2021_JWT or VC20_BSSL_JWT) for a specific
 * credential type from the metadata embedded in the generation context.
 */
@Slf4j
public class ProfileExtractor {

    /**
     * Extract ProfileId for a specific credential type from the generation context.
     * The profile information is passed via the enriched claims metadata.
     *
     * @param credentialType The credential type to extract profile for
     * @param context The generation context
     * @return ProfileId for the credential type, or VC20_BSSL_JWT as default
     */
    public ProfileId extractProfile(String credentialType, CredentialGenerationContext context) {
        Map<String, Object> requestedClaims = context.getRequestedClaims();
        if (requestedClaims != null && requestedClaims.containsKey("__credentialMetadata")) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> metadata = (Map<String, Map<String, Object>>) requestedClaims.get("__credentialMetadata");

            if (metadata.containsKey(credentialType)) {
                Map<String, Object> credMetadata = metadata.get(credentialType);
                String profileStr = (String) credMetadata.get("profile");
                if (profileStr != null) {
                    ProfileId profile = ProfileId.fromString(profileStr);
                    if (profile != null) {
                        log.debug("Using profile {} for credential type {} from metadata", profileStr, credentialType);
                        return profile;
                    }
                }
            }
        }

        // Default to VC 2.0 (recommended by DCP spec)
        log.debug("No profile found in metadata for {}, using default VC20_BSSL_JWT", credentialType);
        return ProfileId.VC20_BSSL_JWT;
    }
}
