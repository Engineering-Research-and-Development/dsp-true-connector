package it.eng.dcp.issuer.service.jwt;

import it.eng.dcp.common.config.BaseDidDocumentConfiguration;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.common.service.KeyService;

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

    private final String issuerDid;
    private final KeyService keyService;
    private final BaseDidDocumentConfiguration didDocumentConfig;

    /**
     * Create a new JWT generator factory.
     *
     * @param issuerDid The issuer's DID
     * @param keyService Service for retrieving signing keys
     * @param didDocumentConfig DID document configuration
     */
    public VcJwtGeneratorFactory(String issuerDid, KeyService keyService, BaseDidDocumentConfiguration didDocumentConfig) {
        this.issuerDid = issuerDid;
        this.keyService = keyService;
        this.didDocumentConfig = didDocumentConfig;
    }

    /**
     * Create a JWT generator for the specified profile.
     *
     * @param profileId The DCP profile ID
     * @return The appropriate JWT generator for the profile
     */
    public VcJwtGenerator createGenerator(ProfileId profileId) {
        if (profileId == ProfileId.VC20_BSSL_JWT) {
            return new VC20JwtGenerator(issuerDid, keyService, didDocumentConfig);
        } else {
            return new VC11JwtGenerator(issuerDid, keyService, didDocumentConfig);
        }
    }
}

