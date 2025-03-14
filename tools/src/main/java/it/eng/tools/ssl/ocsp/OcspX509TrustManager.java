package it.eng.tools.ssl.ocsp;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Custom X509TrustManager that adds OCSP validation to certificate verification.
 */
@Slf4j
public class OcspX509TrustManager extends X509ExtendedTrustManager {

    private final X509ExtendedTrustManager delegate;
    private final CachedOcspValidator ocspValidator;
    private final OcspProperties ocspProperties;

    /**
     * Creates a new OCSP X509TrustManager.
     * 
     * @param delegate The delegate trust manager
     * @param ocspValidator The OCSP validator
     * @param ocspProperties The OCSP properties
     */
    public OcspX509TrustManager(X509ExtendedTrustManager delegate, 
                               CachedOcspValidator ocspValidator,
                               OcspProperties ocspProperties) {
        this.delegate = delegate;
        this.ocspValidator = ocspValidator;
        this.ocspProperties = ocspProperties;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.checkClientTrusted(chain, authType);
        if (ocspProperties.isEnabled()) {
            validateWithOcsp(chain);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.checkServerTrusted(chain, authType);
        if (ocspProperties.isEnabled()) {
            validateWithOcsp(chain);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        delegate.checkClientTrusted(chain, authType, socket);
        if (ocspProperties.isEnabled()) {
            validateWithOcsp(chain);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        delegate.checkServerTrusted(chain, authType, socket);
        if (ocspProperties.isEnabled()) {
            validateWithOcsp(chain);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        delegate.checkClientTrusted(chain, authType, engine);
        if (ocspProperties.isEnabled()) {
            validateWithOcsp(chain);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        delegate.checkServerTrusted(chain, authType, engine);
        if (ocspProperties.isEnabled()) {
            validateWithOcsp(chain);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
    }

    /**
     * Validates a certificate chain using OCSP.
     * 
     * @param chain The certificate chain to validate
     * @throws CertificateException If validation fails and soft-fail is disabled
     */
    private void validateWithOcsp(X509Certificate[] chain) throws CertificateException {
        if (chain == null || chain.length == 0) {
            log.warn("Empty certificate chain");
            if (!ocspProperties.isSoftFail()) {
                throw new CertificateException("Empty certificate chain");
            }
            return;
        }

        try {
            List<X509Certificate> certificates = Arrays.asList(chain);
            
            // Validate each certificate in the chain except the root CA
            for (int i = 0; i < certificates.size() - 1; i++) {
                X509Certificate certificate = certificates.get(i);
                X509Certificate issuerCertificate = certificates.get(i + 1);
                
                boolean valid = ocspValidator.validate(certificate, issuerCertificate);
                
                if (!valid && !ocspProperties.isSoftFail()) {
                    throw new CertificateException("OCSP validation failed for certificate: " + 
                            certificate.getSubjectX500Principal());
                } else if (!valid) {
                    log.warn("OCSP validation failed for certificate but soft-fail is enabled: {}", 
                            certificate.getSubjectX500Principal());
                }
            }
        } catch (Exception e) {
            log.error("Error during OCSP validation", e);
            if (!ocspProperties.isSoftFail()) {
                throw new CertificateException("OCSP validation error: " + e.getMessage(), e);
            }
        }
    }
}
