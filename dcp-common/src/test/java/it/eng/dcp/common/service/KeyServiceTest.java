package it.eng.dcp.common.service;

import com.nimbusds.jose.jwk.Curve;
import it.eng.dcp.common.config.DidDocumentConfig;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeyServiceTest {

    @Mock
    private KeyMetadataService keyMetadataService;

    @InjectMocks
    private KeyService keyService;

    @Test
    @DisplayName("getSigningJwk loads ECKey from mocked InputStream (in-memory keystore, no file I/O)")
    void getSigningJwkLoadsFromMockedInputStream() throws Exception {
        // Generate EC KeyPair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);
        KeyPair kp = kpg.generateKeyPair();

        // Create a self-signed certificate using BouncyCastle
        X500Name dnName = new X500Name("CN=Test");
        BigInteger certSerialNumber = BigInteger.valueOf(System.currentTimeMillis());
        Date startDate = new Date();
        Date endDate = new Date(System.currentTimeMillis() + 86400000L);
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName, certSerialNumber, startDate, endDate, dnName, kp.getPublic());
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(certBuilder.build(contentSigner));

        // Store KeyPair in in-memory PKCS12 keystore
        String password = "password";
        String alias = "test-alias";
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, password.toCharArray());
        ks.setKeyEntry(alias, kp.getPrivate(), password.toCharArray(), new Certificate[]{cert});
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, password.toCharArray());
        byte[] keystoreBytes = baos.toByteArray();
        baos.close();
        ByteArrayInputStream bais = new ByteArrayInputStream(keystoreBytes);

        // Mock getResourceAsStream to return our in-memory keystore
        ClassLoader cl = mock(ClassLoader.class);
        when(cl.getResourceAsStream(anyString())).thenReturn(bais);
        KeyService keyServiceSpy = spy(new KeyService(keyMetadataService));
        doReturn(cl).when(keyServiceSpy).getClassLoader();

        // Prepare config
        DidDocumentConfig config = DidDocumentConfig.builder()
                .did("did:web:example.com")
                .keystorePath("mocked.p12")
                .keystorePassword(password)
                .keystoreAlias(alias)
                .build();

        // call getSigningJwk and validate
        var signing = keyServiceSpy.getSigningJwk(config);
        assertNotNull(signing, "signing JWK must not be null");
        assertEquals(keyServiceSpy.getKidFromPublicKey(config), signing.getKeyID(), "kid must match computed kid from public key");
        assertNotNull(signing.toPublicJWK());
        assertEquals(Curve.P_256, signing.getCurve());
    }

    @Test
    @DisplayName("rotateKeyAndUpdateMetadata returns new alias and updates metadata (success)")
    void rotateKeyAndUpdateMetadata_success() throws Exception {
        // Use a temp file for keystore
        File tempKeystore = File.createTempFile("test-keystore", ".p12");
        tempKeystore.deleteOnExit();
        String password = "testpass";
        // Create an empty PKCS12 keystore
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, password.toCharArray());
        try (FileOutputStream fos = new FileOutputStream(tempKeystore)) {
            ks.store(fos, password.toCharArray());
        }
        KeyService keyServiceSpy = spy(new KeyService(keyMetadataService));
        doNothing().when(keyServiceSpy).invalidateAllCache();
        doCallRealMethod().when(keyServiceSpy).rotateAndPersistKeyPair(anyString(), anyString(), anyString());

        // Call method
        String alias = keyServiceSpy.rotateKeyAndUpdateMetadata(tempKeystore.getAbsolutePath(), password, "dsptrueconnector-");

        assertNotNull(alias);
        assertTrue(alias.startsWith("dsptrueconnector-"));
        verify(keyMetadataService).saveNewKeyMetadata(alias);
        verify(keyServiceSpy).invalidateAllCache();

        // --- NEW: Verify rotated key can be read ---
        KeyStore ks2 = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(tempKeystore)) {
            ks2.load(fis, password.toCharArray());
        }
        boolean aliasFound = false;
        System.out.println("Aliases in keystore after rotation:");
        for (java.util.Enumeration<String> e = ks2.aliases(); e.hasMoreElements(); ) {
            String a = e.nextElement();
            System.out.println("  alias: " + a);
            if (a.equals(alias)) aliasFound = true;
        }
    }

    @Test
    @DisplayName("rotateKeyAndUpdateMetadata throws exception if rotation fails")
    void rotateKeyAndUpdateMetadata_failure() {
        KeyService keyServiceSpy = spy(new KeyService(keyMetadataService));
        // Force rotateAndPersistKeyPair to throw
        doThrow(new RuntimeException("Rotation failed")).when(keyServiceSpy)
                .rotateAndPersistKeyPair(anyString(), anyString(), anyString());
        String path = "dummy.p12";
        String password = "failpass";
        assertThrows(RuntimeException.class, () -> keyServiceSpy.rotateKeyAndUpdateMetadata(path, password, "dsptrueconnector-"));
    }
}
