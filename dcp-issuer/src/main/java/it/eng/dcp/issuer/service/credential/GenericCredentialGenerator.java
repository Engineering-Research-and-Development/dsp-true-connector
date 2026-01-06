package it.eng.dcp.issuer.service.credential;

import it.eng.dcp.common.model.CredentialGenerationContext;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.issuer.service.jwt.VcJwtGeneratorFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic credential generator for unknown or custom credential types.
 *
 * <p>Generates credentials with basic claims when no specific generator
 * is available for the requested credential type.
 */
@Slf4j
public class GenericCredentialGenerator implements CredentialGenerator {

    private final VcJwtGeneratorFactory jwtGeneratorFactory;
    private final ProfileExtractor profileExtractor;
    private final String issuerDid;
    private final String credentialType;

    /**
     * Create a new generic credential generator.
     *
     * @param jwtGeneratorFactory Factory for creating profile-specific JWT generators
     * @param profileExtractor Extractor for determining credential profile
     * @param issuerDid The issuer's DID
     * @param credentialType The credential type to generate
     */
    public GenericCredentialGenerator(VcJwtGeneratorFactory jwtGeneratorFactory,
                                     ProfileExtractor profileExtractor,
                                     String issuerDid,
                                     String credentialType) {
        this.jwtGeneratorFactory = jwtGeneratorFactory;
        this.profileExtractor = profileExtractor;
        this.issuerDid = issuerDid;
        this.credentialType = credentialType;
    }

    @Override
    public CredentialMessage.CredentialContainer generateCredential(CredentialGenerationContext context) {
        log.warn("Unknown credential type '{}', generating generic credential", credentialType);

        ProfileId profile = profileExtractor.extractProfile(credentialType, context);

        Map<String, String> claims = new HashMap<>();
        claims.put("status", "Active");
        claims.put("issuedBy", issuerDid);

        String signedJwt = jwtGeneratorFactory
                .createGenerator(profile)
                .generateJwt(context.getRequest().getHolderPid(), credentialType, claims);

        return CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType(credentialType)
                .format("jwt")
                .payload(signedJwt)
                .build();
    }

    @Override
    public String getCredentialType() {
        return credentialType;
    }
}

