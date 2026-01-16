package it.eng.dcp.issuer.integration;

import it.eng.dcp.common.service.KeyService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Use only locally to test key rotation functionality")
public class KeyRotationIT {
    @Autowired
    private KeyService keyService;

    @Test
    void rotateKeyIntegration() throws Exception {
        // These should match your test application.properties
        String keystorePath = "c:/Users/igobalog/work/code/engineering/dsp-true-connector/dcp-issuer/src/test/resources/eckey-issuer.p12";
        String password = "password";
//        String aliasPrefix = "dcp-issuer";
        String aliasPrefix = "dcp-issuer-1768228663653";
//        keyService.rotateAndPersistKeyPair(keystorePath, password, aliasPrefix);
        System.out.println("Key rotation completed for keystore: " + keystorePath);

        // Find the new alias (with timestamp)
        java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
        try (java.io.FileInputStream fis = new java.io.FileInputStream(keystorePath)) {
            ks.load(fis, password.toCharArray());
        }
        String foundAlias = null;
        java.util.Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (alias.startsWith(aliasPrefix)) {
                foundAlias = alias;
                break;
            }
        }
        System.out.println("Rotated alias found: " + foundAlias);
        assert foundAlias != null : "Rotated alias not found in keystore!";
        java.security.PrivateKey privateKey = (java.security.PrivateKey) ks.getKey(foundAlias, password.toCharArray());
        java.security.cert.Certificate cert = ks.getCertificate(foundAlias);
        assert privateKey != null : "Rotated private key must not be null";
        assert cert != null : "Rotated certificate must not be null";
        assert cert.getPublicKey() != null : "Rotated public key must not be null";
        System.out.println("Rotated keypair is valid and accessible.");
    }
}
