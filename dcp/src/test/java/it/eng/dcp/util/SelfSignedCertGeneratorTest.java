package it.eng.dcp.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

class SelfSignedCertGeneratorTest {

    @Test
    @DisplayName("GeneratesValidSelfSignedCertificate")
    void generatesValidSelfSignedCertificate() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        KeyPair keyPair = keyGen.generateKeyPair();
        String dn = "CN=Test, O=Example, C=US";
        X509Certificate cert = SelfSignedCertGenerator.generate(dn, keyPair);

        LdapName expectedDn = new LdapName(dn);
        LdapName actualDn = new LdapName(cert.getSubjectX500Principal().getName());
        Assertions.assertEquals(expectedDn.size(), actualDn.size());
        for (Rdn expectedRdn : expectedDn.getRdns()) {
            boolean found = actualDn.getRdns().stream().anyMatch(rdn ->
                    rdn.getType().equalsIgnoreCase(expectedRdn.getType()) &&
                            rdn.getValue().equals(expectedRdn.getValue())
            );
            Assertions.assertTrue(found, "Missing RDN: " + expectedRdn);
        }
    }

    @Test
    @DisplayName("ThrowsExceptionForNullKeyPair")
    void throwsExceptionForNullKeyPair() {
        String dn = "CN=Test, O=Example, C=US";
        Assertions.assertThrows(Exception.class, () -> SelfSignedCertGenerator.generate(dn, null));
    }

    @Test
    @DisplayName("ThrowsExceptionForNullDn")
    void throwsExceptionForNullDn() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        KeyPair keyPair = keyGen.generateKeyPair();
        Assertions.assertThrows(Exception.class, () -> SelfSignedCertGenerator.generate(null, keyPair));
    }

    @Test
    @DisplayName("ThrowsExceptionForEmptyDn")
    void throwsExceptionForEmptyDn() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(256);
        KeyPair keyPair = keyGen.generateKeyPair();
        Assertions.assertThrows(Exception.class, () -> SelfSignedCertGenerator.generate("", keyPair));
    }
}
