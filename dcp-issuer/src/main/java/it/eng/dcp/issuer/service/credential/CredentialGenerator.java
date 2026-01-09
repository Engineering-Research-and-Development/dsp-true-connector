package it.eng.dcp.issuer.service.credential;

import it.eng.dcp.common.model.CredentialGenerationContext;
import it.eng.dcp.common.model.CredentialMessage;

/**
 * Interface for generating specific types of verifiable credentials.
 * Implementations provide credential-type-specific claim generation logic.
 */
public interface CredentialGenerator {

    /**
     * Generate a credential of a specific type.
     *
     * @param context The generation context containing request, claims, and constraints
     * @return A credential container with the generated credential
     */
    CredentialMessage.CredentialContainer generateCredential(CredentialGenerationContext context);

    /**
     * Generate a credential of a specific type, with status list info.
     *
     * @param context The generation context containing request, claims, and constraints
     * @param statusListId The status list resource ID (nullable for non-status-list profiles)
     * @param statusListIndex The status list index (nullable for non-status-list profiles)
     * @return A credential container with the generated credential
     */
    default CredentialMessage.CredentialContainer generateCredential(CredentialGenerationContext context, String statusListId, Integer statusListIndex) {
        throw new UnsupportedOperationException("Status list not supported for this credential type");
    }

    /**
     * Get the credential type this generator handles.
     *
     * @return The credential type identifier (e.g., "MembershipCredential")
     */
    String getCredentialType();
}
