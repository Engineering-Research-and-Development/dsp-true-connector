package it.eng.dcp.service;

import com.nimbusds.jose.jwk.JWKSet;
import it.eng.dcp.core.InMemoryDidResolverService;
import it.eng.dcp.core.InMemoryJtiReplayCache;
import it.eng.dcp.config.DcpProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

//TODO refactor to proper integration test, like ones from connector module
class KeyServiceIT {

    private Path tempKeystore;

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
        KeyService keyService = new KeyService(null);

        // rotate and persist a new key pair into the temporary keystore
        keyService.rotateAndPersistKeyPair(keystorePath, password, alias);

        // after rotation, keyPair should be available and getSigningJwk should succeed
        var signingJwk = keyService.getSigningJwk();
        assertNotNull(signingJwk);
        assertNotNull(signingJwk.getKeyID());

        // Prepare token service using real keyService
        DcpProperties props = new DcpProperties();
        props.setConnectorDid("did:example:connector");

        InMemoryDidResolverService didResolver = new InMemoryDidResolverService();
        InMemoryJtiReplayCache jtiCache = new InMemoryJtiReplayCache();

        SelfIssuedIdTokenService svc = new SelfIssuedIdTokenService(props, didResolver, jtiCache, keyService);

        // register public JWK in resolver so validation can fetch it
        didResolver.put(props.getConnectorDid(), new JWKSet(signingJwk.toPublicJWK()));

        // create and validate token using the real signing key
        String token = svc.createAndSignToken("did:example:aud", null);
        assertNotNull(token);

        var claims = svc.validateToken(token);
        assertEquals(props.getConnectorDid(), claims.getIssuer());

        // Disable replay detection for now
        // second validation should be rejected due to replay detection
//        assertThrows(IllegalStateException.class, () -> svc.validateToken(token));
    }
}
