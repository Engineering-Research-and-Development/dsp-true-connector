package it.eng.dcp.util;


import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

public class SelfSignedCertGenerator {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static X509Certificate generate(String dn, KeyPair keyPair) throws Exception {
        if (dn == null || dn.isEmpty() || keyPair == null) {
            throw new IllegalArgumentException("Distinguished name and key pair must not be null or empty");
        }

        long now = System.currentTimeMillis();
        Date from = new Date(now);
        Date to = new Date(now + 365L * 24 * 60 * 60 * 1000); // 1 year validity
        BigInteger sn = new BigInteger(64, new java.security.SecureRandom());

        org.bouncycastle.asn1.x500.X500Name subject = new org.bouncycastle.asn1.x500.X500Name(dn);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                sn,
                from,
                to,
                subject,
                keyPair.getPublic()
        );

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider("BC")
                .build(keyPair.getPrivate());

        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));
    }

}
