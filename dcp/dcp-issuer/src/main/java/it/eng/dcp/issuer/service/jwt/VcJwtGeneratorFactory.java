package it.eng.dcp.issuer.service.jwt;

import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.issuer.service.IssuerIdentity;

/**
 * Factory for creating profile-specific VC JWT generators.
 *
 * <p>This factory creates the appropriate JWT generator based on the DCP profile ID:
 * <ul>
 *   <li>VC20_BSSL_JWT -> VC20JwtGenerator (VC Data Model 2.0 with BitstringStatusList)</li>
 *   <li>VC11_SL2021_JWT -> VC11JwtGenerator (VC Data Model 1.1 with StatusList2021)</li>
 * </ul>
 */
public class VcJwtGeneratorFactory {

    private final IssuerIdentity issuerIdentity;

    /**
     * Create a new JWT generator factory.
     *
     * @param issuerIdentity The single Issuer identity
     */
    public VcJwtGeneratorFactory(IssuerIdentity issuerIdentity) {
        this.issuerIdentity = issuerIdentity;
    }

    /**
     * Create a JWT generator for the specified profile.
     *
     * @param profileId The DCP profile ID
     * @return The appropriate JWT generator for the profile
     */
    public VcJwtGenerator createGenerator(ProfileId profileId) {
        if (profileId == ProfileId.VC20_BSSL_JWT) {
            return new VC20JwtGenerator(issuerIdentity);
        } else {
            return new VC11JwtGenerator(issuerIdentity);
        }
    }
}

