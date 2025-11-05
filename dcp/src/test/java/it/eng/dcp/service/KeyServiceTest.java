package it.eng.dcp.service;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.Curve;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

import static org.junit.jupiter.api.Assertions.*;

class KeyServiceTest {

    @Test
    @DisplayName("getSigningJwk builds ECKey from internal KeyPair")
    void getSigningJwkBuildsEcKey() throws Exception {
        KeyService ks = new KeyService(null);

        // generate a transient ECKey and derive a KeyPair
        ECKey ec = ks.generateEcKey();
        ECPublicKey pub = ec.toECPublicKey();
        ECPrivateKey priv = ec.toECPrivateKey();
        KeyPair kp = new KeyPair(pub, priv);

        // inject the KeyPair into the KeyService instance via reflection
        Field f = KeyService.class.getDeclaredField("keyPair");
        f.setAccessible(true);
        f.set(ks, kp);

        // call getSigningJwk and validate
        var signing = ks.getSigningJwk();
        assertNotNull(signing, "signing JWK must not be null");
        assertEquals(ks.getKidFromPublicKey(), signing.getKeyID(), "kid must match computed kid from public key");
        assertNotNull(signing.toPublicJWK());
        assertEquals(Curve.P_256, signing.getCurve());
    }
}

