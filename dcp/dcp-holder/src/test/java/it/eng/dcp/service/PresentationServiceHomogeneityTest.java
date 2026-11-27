package it.eng.dcp.service;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import it.eng.dcp.common.model.PresentationQueryMessage;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantKeyProvider;
import it.eng.dcp.common.participant.ParticipantState;
import it.eng.dcp.common.participant.SigningKeyReference;
import it.eng.dcp.holder.model.VerifiableCredential;
import it.eng.dcp.holder.repository.VerifiableCredentialRepository;
import it.eng.dcp.holder.service.BasicVerifiablePresentationSigner;
import it.eng.dcp.holder.service.PresentationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class PresentationServiceHomogeneityTest {

    private static final String HOLDER_DID = "did:holder:1";
    private static final Set<String> HOLDER_DIDS = Set.of(HOLDER_DID);

    private VerifiableCredentialRepository repo;
    private ParticipantKeyProvider keyProvider;
    private BasicVerifiablePresentationSigner signer;
    private PresentationService svc;
    private ParticipantContext participant;

    @BeforeEach
    void setup() throws Exception {
        repo = Mockito.mock(VerifiableCredentialRepository.class);
        keyProvider = Mockito.mock(ParticipantKeyProvider.class);
        participant = new ParticipantContext("default", HOLDER_DID, new SigningKeyReference("eckey.p12", "alias"),
                List.of(), Map.of(), null, ParticipantState.ENABLED);

        // Generate a test EC key for signing
        ECKey testKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key-id")
                .generate();

        // Mock key provider to return test key
        when(keyProvider.activeSigningKey(participant)).thenReturn(testKey);

        signer = new BasicVerifiablePresentationSigner(keyProvider);
        svc = new PresentationService(repo, signer);
    }

    @Test
    void mixedProfilesProduceMultiplePresentations() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(HOLDER_DID)
                .claim("scope", List.of("typeA", "typeB"))
                .build();

        VerifiableCredential a = VerifiableCredential.Builder.newInstance()
                .holderDid(HOLDER_DID)
                .credentialType("typeA")
                .profileId(ProfileId.VC11_SL2021_JWT)
                .issuanceDate(Instant.now())
                .credentialIds(List.of("c1"))
                .build();

        VerifiableCredential b = VerifiableCredential.Builder.newInstance()
                .holderDid(HOLDER_DID)
                .credentialType("typeB")
                .profileId(ProfileId.VC20_BSSL_JWT)
                .issuanceDate(Instant.now())
                .credentialIds(List.of("c2"))
                .build();

        when(repo.findByHolderDidInAndCredentialTypeIn(HOLDER_DIDS, List.of("typeA", "typeB"))).thenReturn(List.of(a, b));

        var query = PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("typeA", "typeB"))
                .build();

        var resp = svc.createPresentation(query, HOLDER_DID, claims, participant);
        assertNotNull(resp);
        assertTrue(resp.getPresentation().size() >= 2, "Should produce separate presentations for different profiles");
    }

    @Test
    void singleProfileGroupsAll() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(HOLDER_DID)
                .claim("scope", List.of("typeA"))
                .build();

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

        when(repo.findByHolderDidInAndCredentialTypeIn(HOLDER_DIDS, List.of("typeA"))).thenReturn(List.of(a, b));

        var query = PresentationQueryMessage.Builder.newInstance()
                .scope(List.of("typeA"))
                .build();

        var resp = svc.createPresentation(query, HOLDER_DID, claims, participant);
        assertNotNull(resp);
        assertEquals(1, resp.getPresentation().size(), "Single profile should yield one presentation grouping both creds");
    }
}
