package it.eng.dcp.service;

import com.nimbusds.jose.jwk.JWKSet;
import it.eng.dcp.common.config.DidDocumentConfig;
import it.eng.dcp.common.participant.ParticipantContext;
import it.eng.dcp.common.participant.ParticipantKeyProvider;
import it.eng.dcp.common.participant.ParticipantState;
import it.eng.dcp.common.participant.SigningKeyReference;
import it.eng.dcp.common.service.KeyService;
import it.eng.dcp.common.service.audit.DcpAuditEventPublisher;
import it.eng.dcp.common.service.did.InMemoryDidResolverService;
import it.eng.dcp.common.service.sts.InMemoryJtiReplayCache;
import it.eng.dcp.common.service.sts.SelfIssuedIdTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

//TODO refactor to proper integration test, like ones from connector module
@Disabled
class KeyServiceIT {

    private Path tempKeystore;
    private DidDocumentConfig config;
    private DcpAuditEventPublisher auditEventPublisher;

    @AfterEach
    void cleanup() throws Exception {
        if (tempKeystore != null && Files.exists(tempKeystore)) {
            Files.deleteIfExists(tempKeystore);
        }
    }

    @Test
    @DisplayName("KeyService rotates and persists keystore; SelfIssuedIdTokenService signs and validates using KeyService")
    void keyServiceSigningPathWorks() throws Exception {
        // create a temporary keystore path (file should NOT exist so KeyService will create it)
        tempKeystore = Files.createTempFile("test-eckey-", ".p12");
        // delete the temp file so rotateAndPersistKeyPair treats it as non-existing (it will create the keystore)
        Files.deleteIfExists(tempKeystore);
        String keystorePath = tempKeystore.toAbsolutePath().toString();
        String password = "password";
        String alias = "testalias";

        // instantiate KeyService (KeyMetadataService not required for rotateAndPersistKeyPair)
        KeyService keyService = new KeyService(null, auditEventPublisher);

        // rotate and persist a new key pair into the temporary keystore
        keyService.rotateAndPersistKeyPair(keystorePath, password, alias);

        // after rotation, keyPair should be available and getSigningJwk should succeed
        var signingJwk = keyService.getSigningJwk(config);
        assertNotNull(signingJwk);
        assertNotNull(signingJwk.getKeyID());

        // Prepare token service using real keyService
        String connectorDid = "did:example:connector";
        ParticipantContext participant = new ParticipantContext("default", connectorDid,
                new SigningKeyReference(keystorePath, alias), List.of(), Map.of(), null, ParticipantState.ENABLED);
        ParticipantKeyProvider keyProvider = Mockito.mock(ParticipantKeyProvider.class);
        Mockito.when(keyProvider.activeSigningKey(participant)).thenReturn(signingJwk);

        InMemoryDidResolverService didResolver = new InMemoryDidResolverService();
        InMemoryJtiReplayCache jtiCache = new InMemoryJtiReplayCache();

        SelfIssuedIdTokenService svc = new SelfIssuedIdTokenService(didResolver, jtiCache, keyProvider, auditEventPublisher);

        // register public JWK in resolver so validation can fetch it
        didResolver.put(connectorDid, new JWKSet(signingJwk.toPublicJWK()));

        // create and validate token using the real signing key
        String token = svc.createAndSignToken("did:example:aud", null, participant);
        assertNotNull(token);

        var claims = svc.validateToken(token, participant);
        assertEquals(connectorDid, claims.getIssuer());

        // Disable replay detection for now
        // second validation should be rejected due to replay detection
//        assertThrows(IllegalStateException.class, () -> svc.validateToken(token));
    }
}
