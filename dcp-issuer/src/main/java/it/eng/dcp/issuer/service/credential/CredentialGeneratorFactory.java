package it.eng.dcp.issuer.service.credential;

import it.eng.dcp.issuer.service.jwt.VcJwtGeneratorFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating credential type-specific generators.
 *
 * <p>This factory creates the appropriate credential generator based on the
 * requested credential type:
 * <ul>
 *   <li>MembershipCredential -> MembershipCredentialGenerator</li>
 *   <li>OrganizationCredential -> OrganizationCredentialGenerator</li>
 *   <li>Other types -> GenericCredentialGenerator</li>
 * </ul>
 */
public class CredentialGeneratorFactory {

    private final Map<String, CredentialGenerator> generators;
    private final VcJwtGeneratorFactory jwtGeneratorFactory;
    private final ProfileExtractor profileExtractor;
    private final String issuerDid;

    /**
     * Create a new credential generator factory.
     *
     * @param jwtGeneratorFactory Factory for creating profile-specific JWT generators
     * @param issuerDid The issuer's DID
     */
    public CredentialGeneratorFactory(VcJwtGeneratorFactory jwtGeneratorFactory, String issuerDid) {
        this.jwtGeneratorFactory = jwtGeneratorFactory;
        this.issuerDid = issuerDid;
        this.profileExtractor = new ProfileExtractor();
        this.generators = new HashMap<>();

        // Register known credential generators
        registerGenerator(new MembershipCredentialGenerator(jwtGeneratorFactory, profileExtractor));
        registerGenerator(new OrganizationCredentialGenerator(jwtGeneratorFactory, profileExtractor));
    }

    /**
     * Register a credential generator.
     *
     * @param generator The generator to register
     */
    private void registerGenerator(CredentialGenerator generator) {
        generators.put(generator.getCredentialType(), generator);
    }

    /**
     * Create a credential generator for the specified credential type.
     *
     * @param credentialType The credential type to generate
     * @return The appropriate credential generator
     */
    public CredentialGenerator createGenerator(String credentialType) {
        // Return registered generator if available
        if (generators.containsKey(credentialType)) {
            return generators.get(credentialType);
        }

        // Fall back to generic generator for unknown types
        return new GenericCredentialGenerator(jwtGeneratorFactory, profileExtractor, issuerDid, credentialType);
    }
}

