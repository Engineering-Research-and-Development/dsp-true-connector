package it.eng.dcp.issuer.service.credential;

import it.eng.dcp.common.model.CredentialGenerationContext;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.issuer.service.jwt.VcJwtGeneratorFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generator for MembershipCredential type credentials.
 *
 * <p>Generates credentials containing membership-related claims such as
 * membership type, status, and unique identifier.
 */
public class MembershipCredentialGenerator implements CredentialGenerator {

    private final VcJwtGeneratorFactory jwtGeneratorFactory;
    private final ProfileExtractor profileExtractor;

    /**
     * Create a new membership credential generator.
     *
     * @param jwtGeneratorFactory Factory for creating profile-specific JWT generators
     * @param profileExtractor Extractor for determining credential profile
     */
    public MembershipCredentialGenerator(VcJwtGeneratorFactory jwtGeneratorFactory, ProfileExtractor profileExtractor) {
        this.jwtGeneratorFactory = jwtGeneratorFactory;
        this.profileExtractor = profileExtractor;
    }

    @Override
    public CredentialMessage.CredentialContainer generateCredential(CredentialGenerationContext context) {
        ProfileId profile = profileExtractor.extractProfile(getCredentialType(), context);

        Map<String, String> claims = new HashMap<>();
        claims.put("membershipType", "Premium");
        claims.put("status", "Active");
        claims.put("membershipId", "MEMBER-" + UUID.randomUUID().toString().substring(0, 8));

        String signedJwt = jwtGeneratorFactory
                .createGenerator(profile)
                .generateJwt(context.getRequest().getHolderPid(), getCredentialType(), claims);

        return CredentialMessage.CredentialContainer.Builder.newInstance()
                .credentialType(getCredentialType())
                .format("jwt")
                .payload(signedJwt)
                .build();
    }

    @Override
    public String getCredentialType() {
        return "MembershipCredential";
    }
}

