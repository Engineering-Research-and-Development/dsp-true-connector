package it.eng.dcp.common.service;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.KeyUse;
import it.eng.dcp.common.config.DidDocumentConfig;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        ks.store(baos, password.toCharArray());
        byte[] keystoreBytes = baos.toByteArray();
        baos.close();
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(keystoreBytes);

        // Mock getResourceAsStream to return our in-memory keystore
        ClassLoader cl = Mockito.mock(ClassLoader.class);
        Mockito.when(cl.getResourceAsStream(Mockito.anyString())).thenReturn(bais);
        KeyService keyServiceSpy = Mockito.spy(new KeyService(keyMetadataService));
        Mockito.doReturn(cl).when(keyServiceSpy).getClassLoader();

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
}
