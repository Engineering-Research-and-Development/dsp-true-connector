package it.eng.dcp.issuer.service.credential;

import it.eng.dcp.common.model.CredentialGenerationContext;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.ProfileId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileExtractorTest {

    private final ProfileExtractor extractor = new ProfileExtractor();

    private static CredentialRequest dummyRequest() {
        // Minimal dummy request, adjust fields as needed for your model
        return new CredentialRequest();
    }

    @Test
    void extractProfile_returnsVc11Profile_whenPresent() {
        Map<String, Object> credMetadata = new HashMap<>();
        credMetadata.put("profile", "vc11-sl2021/jwt");
        Map<String, Map<String, Object>> metadata = new HashMap<>();
        metadata.put("MembershipCredential", credMetadata);
        Map<String, Object> requestedClaims = new HashMap<>();
        requestedClaims.put("__credentialMetadata", metadata);
        CredentialGenerationContext context = CredentialGenerationContext.Builder.newInstance()
                .request(dummyRequest())
                .requestedClaims(requestedClaims)
                .build();

        ProfileId result = extractor.extractProfile("MembershipCredential", context);
        assertEquals(ProfileId.VC11_SL2021_JWT, result);
    }

    @Test
    void extractProfile_returnsVc20Profile_whenPresent() {
        Map<String, Object> credMetadata = new HashMap<>();
        credMetadata.put("profile", "vc20-bssl/jwt");
        Map<String, Map<String, Object>> metadata = new HashMap<>();
        metadata.put("OrganizationCredential", credMetadata);
        Map<String, Object> requestedClaims = new HashMap<>();
        requestedClaims.put("__credentialMetadata", metadata);
        CredentialGenerationContext context = CredentialGenerationContext.Builder.newInstance()
                .request(dummyRequest())
                .requestedClaims(requestedClaims)
                .build();

        ProfileId result = extractor.extractProfile("OrganizationCredential", context);
        assertEquals(ProfileId.VC20_BSSL_JWT, result);
    }

    @Test
    void extractProfile_returnsDefault_whenNoProfilePresent() {
        Map<String, Object> credMetadata = new HashMap<>();
        Map<String, Map<String, Object>> metadata = new HashMap<>();
        metadata.put("MembershipCredential", credMetadata);
        Map<String, Object> requestedClaims = new HashMap<>();
        requestedClaims.put("__credentialMetadata", metadata);
        CredentialGenerationContext context = CredentialGenerationContext.Builder.newInstance()
                .request(dummyRequest())
                .requestedClaims(requestedClaims)
                .build();

        ProfileId result = extractor.extractProfile("MembershipCredential", context);
        assertEquals(ProfileId.VC20_BSSL_JWT, result);
    }

    @Test
    void extractProfile_returnsDefault_whenNoMetadata() {
        Map<String, Object> requestedClaims = new HashMap<>();
        CredentialGenerationContext context = CredentialGenerationContext.Builder.newInstance()
                .request(dummyRequest())
                .requestedClaims(requestedClaims)
                .build();

        ProfileId result = extractor.extractProfile("MembershipCredential", context);
        assertEquals(ProfileId.VC20_BSSL_JWT, result);
    }

    @Test
    void extractProfile_returnsDefault_whenRequestedClaimsNull() {
        CredentialGenerationContext context = CredentialGenerationContext.Builder.newInstance()
                .request(dummyRequest())
                .requestedClaims(null)
                .build();

        ProfileId result = extractor.extractProfile("MembershipCredential", context);
        assertEquals(ProfileId.VC20_BSSL_JWT, result);
    }
}
