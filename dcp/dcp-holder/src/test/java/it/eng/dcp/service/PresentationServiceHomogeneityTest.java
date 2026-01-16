package it.eng.dcp.service;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.model.VerifiableCredential;
import it.eng.dcp.repository.VerifiableCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PresentationServiceHomogeneityTest {

    private VerifiableCredentialRepository repo;
    private KeyService keyService;
    private BasicVerifiablePresentationSigner signer;
    private PresentationService svc;
    private DidDocumentConfig config;

    @BeforeEach
    void setup() throws Exception {
        repo = Mockito.mock(VerifiableCredentialRepository.class);
        keyService = Mockito.mock(KeyService.class);
        config = Mockito.mock(DidDocumentConfig.class);

        // Generate a test EC key for signing
        ECKey testKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key-id")
                .generate();

        // Mock KeyService to return test key
        when(keyService.getSigningJwk(config)).thenReturn(testKey);

        signer = new BasicVerifiablePresentationSigner(keyService, config);
        svc = new PresentationService(repo, signer);
    }

    @Test
    void mixedProfilesProduceMultiplePresentations() {
        VerifiableCredential a = VerifiableCredential.Builder.newInstance()
                .holderDid("did:holder:1")
                .credentialType("typeA")
                .profileId(ProfileId.VC11_SL2021_JWT)
                .issuanceDate(Instant.now())
                .credentialIds(List.of("c1"))
                .build();

        VerifiableCredential b = VerifiableCredential.Builder.newInstance()
                .holderDid("did:holder:1")
                .credentialType("typeB")
                .profileId(ProfileId.VC20_BSSL_JWT)
                .issuanceDate(Instant.now())
                .credentialIds(List.of("c2"))
                .build();

        when(repo.findByCredentialTypeIn(List.of("typeA", "typeB"))).thenReturn(List.of(a, b));

        var query = it.eng.dcp.model.PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("typeA", "typeB"))
                .build();

        var resp = svc.createPresentation(query);
        assertNotNull(resp);
        assertTrue(resp.getPresentation().size() >= 2, "Should produce separate presentations for different profiles");
    }

    @Test
    void singleProfileGroupsAll() {
        VerifiableCredential a = VerifiableCredential.Builder.newInstance()
                .holderDid("did:holder:1")
                .credentialType("typeA")
                .profileId(ProfileId.VC11_SL2021_JWT)
                .issuanceDate(Instant.now())
                .credentialIds(List.of("c1"))
                .build();

        VerifiableCredential b = VerifiableCredential.Builder.newInstance()
                .holderDid("did:holder:1")
                .credentialType("typeA")
                .profileId(ProfileId.VC11_SL2021_JWT)
                .issuanceDate(Instant.now())
                .credentialIds(List.of("c2"))
                .build();

        when(repo.findByCredentialTypeIn(List.of("typeA"))).thenReturn(List.of(a, b));

        var query = it.eng.dcp.model.PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("typeA"))
                .build();

        var resp = svc.createPresentation(query);
        assertNotNull(resp);
        assertEquals(1, resp.getPresentation().size(), "Single profile should yield one presentation grouping both creds");
    }
}
