package it.eng.dcp.issuer.integration;

import com.nimbusds.jwt.SignedJWT;
import it.eng.dcp.common.model.CredentialMessage;
import it.eng.dcp.common.model.CredentialRequest;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.issuer.config.CredentialMetadataConfig.CredentialConfig;
import it.eng.dcp.issuer.config.CredentialMetadataConfigLoader;
import it.eng.dcp.issuer.service.CredentialIssuanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CredentialIssuanceServiceIT extends BaseIssuerIntegrationTest {

    @Autowired
    private CredentialIssuanceService credentialIssuanceService;

    @Autowired
    private CredentialMetadataConfigLoader credentialMetadataConfigLoader;

    @Test
    public void generateVC11Credentials_Test() throws ParseException {
        var vc11ProfileId = credentialMetadataConfigLoader.credentialMetadataConfig().getSupported().stream()
                .filter(cc -> ProfileId.VC11_SL2021_JWT.getSpecAlias().equals(cc.getProfile()))
                .map(CredentialConfig::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No CredentialConfig with profile 'vc-1.1-profile' found"));
        CredentialRequest request = CredentialRequest.Builder.newInstance()
            .holderPid("did:web:holder.example.com")
            .credentialIds(List.of(vc11ProfileId))
            .build();

        List<CredentialMessage.CredentialContainer> result =
                credentialIssuanceService.generateCredentials(request);

        assertThat(result).isNotEmpty();
        CredentialMessage.CredentialContainer container = result.get(0);

        // Use the actual getter for JWT string
        String jwt = String.valueOf(container.getPayload()); // If this is not correct, use the actual getter available
        assertThat(jwt).isNotBlank();
        assertEquals("jwt", container.getFormat());

        SignedJWT signedJwt = SignedJWT.parse(jwt);

        assertNotNull(signedJwt);
        assertThat(signedJwt.getJWTClaimsSet().getClaims()).containsKey("vc");
        Object vcObj = signedJwt.getJWTClaimsSet().getClaims().get("vc");
        assertThat(vcObj).isInstanceOf(Map.class);
        Map<String, Object> vcClaim = (Map<String, Object>) vcObj;

        assertThat(vcClaim.get("@context")).isInstanceOf(List.class);
//        assertThat(((List<?>) vcClaim.get("@context"))).contains("https://www.w3.org/2018/credentials/v1");
        assertThat(vcClaim.get("credentialStatus")).isInstanceOf(Map.class);
        Map<String, Object> status = (Map<String, Object>) vcClaim.get("credentialStatus");
        assertThat(status.get("type")).isEqualTo("StatusList2021Entry");
        assertThat(vcClaim).containsKeys("type", "credentialSubject", "issuer", "issuanceDate");
        assertThat(vcClaim).containsKey("proof");
    }

    @Test
    public void generateVC20Credentials_Test() throws Exception {
        var vc20ProfileId = credentialMetadataConfigLoader.credentialMetadataConfig().getSupported().stream()
                .filter(cc -> ProfileId.VC20_BSSL_JWT.getSpecAlias().equals(cc.getProfile()))
                .map(CredentialConfig::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No CredentialConfig with profile 'vc-1.1-profile' found"));

        CredentialRequest request = CredentialRequest.Builder.newInstance()
            .holderPid("did:web:holder.example.com")
            .credentialIds(List.of(vc20ProfileId))
            // If your system requires specifying the profile, do so here.
            .build();

        List<CredentialMessage.CredentialContainer> result =
                credentialIssuanceService.generateCredentials(request);

        assertThat(result).isNotEmpty();
        CredentialMessage.CredentialContainer container = result.get(0);

        String jwt = String.valueOf(container.getPayload());
        assertThat(jwt).isNotBlank();
        assertEquals("jwt", container.getFormat());

        SignedJWT signedJwt = SignedJWT.parse(jwt);
        assertNotNull(signedJwt);

        Map<String, Object> claims = signedJwt.getJWTClaimsSet().getClaims();

        // VC 2.0: No "vc" claim, all fields are top-level
        assertThat(claims.containsKey("vc")).isFalse();
        assertThat(claims.get("@context")).isInstanceOf(List.class);
        assertThat(claims.get("credentialStatus")).isInstanceOf(Map.class);

        Map<String, Object> status = (Map<String, Object>) claims.get("credentialStatus");
        assertThat(status.get("type")).isEqualTo("BitstringStatusListEntry");

        assertThat(claims).containsKeys("type", "credentialSubject", "issuer", "validFrom");
        // VC 2.0 does not have a "proof" object
        assertThat(claims).doesNotContainKey("proof");
    }
}
