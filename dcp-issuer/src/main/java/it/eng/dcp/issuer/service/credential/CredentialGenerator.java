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
     * Get the credential type this generator handles.
     *
     * @return The credential type identifier (e.g., "MembershipCredential")
     */
    String getCredentialType();
}

