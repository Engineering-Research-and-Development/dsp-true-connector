package it.eng.dcp.issuer.service.credential;

import it.eng.dcp.common.model.CredentialGenerationContext;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.issuer.service.jwt.VcJwtGeneratorFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Generator for OrganizationCredential type credentials.
 *
 * <p>Generates credentials containing organization-related claims such as
 * organization name, type, and verification status.
 */
public class OrganizationCredentialGenerator implements CredentialGenerator {

    private final VcJwtGeneratorFactory jwtGeneratorFactory;
    private final ProfileExtractor profileExtractor;

    /**
     * Create a new organization credential generator.
     *
     * @param jwtGeneratorFactory Factory for creating profile-specific JWT generators
     * @param profileExtractor Extractor for determining credential profile
     */
    public OrganizationCredentialGenerator(VcJwtGeneratorFactory jwtGeneratorFactory, ProfileExtractor profileExtractor) {
        this.jwtGeneratorFactory = jwtGeneratorFactory;
        this.profileExtractor = profileExtractor;
    }

    @Override
    public CredentialMessage.CredentialContainer generateCredential(CredentialGenerationContext context, String statusListId, Integer statusListIndex) {
        ProfileId profile = profileExtractor.extractProfile(getCredentialType(), context);
        Map<String, String> claims = new HashMap<>();
        claims.put("organizationName", "Example Organization");
        claims.put("organizationType", "Corporation");
        claims.put("status", "Verified");
        String signedJwt;
        if (ProfileId.VC20_BSSL_JWT.equals(profile)) {
            signedJwt = jwtGeneratorFactory.createGenerator(profile)
                .generateJwt(context.getRequest().getHolderPid(), getCredentialType(), claims, statusListId, statusListIndex);
        } else if (ProfileId.VC11_SL2021_JWT.equals(profile)) {
            signedJwt = jwtGeneratorFactory.createGenerator(profile)
                .generateJwt(context.getRequest().getHolderPid(), getCredentialType(), claims, statusListId, statusListIndex);
        } else {
            signedJwt = jwtGeneratorFactory.createGenerator(profile)
                .generateJwt(context.getRequest().getHolderPid(), getCredentialType(), claims);
        }
        return CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType(getCredentialType())
                .format("jwt")
                .payload(signedJwt)
                .build();
    }

    @Override
    public CredentialMessage.CredentialContainer generateCredential(CredentialGenerationContext context) {
        return generateCredential(context, null, null);
    }

    @Override
    public String getCredentialType() {
        return "OrganizationCredential";
    }
}
