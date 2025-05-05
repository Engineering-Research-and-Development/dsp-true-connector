package it.eng.tools.ssl.ocsp;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import org.springframework.boot.ssl.SslBundles;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating OCSP-enabled trust managers.
 */
@Component
@Slf4j
public class OcspTrustManagerFactory {

    private final CachedOcspValidator ocspValidator;
    private final OcspProperties ocspProperties;
	private final SslBundles sslBundles;

    /**
     * Creates a new OCSP trust manager factory.
     * 
     * @param ocspValidator The OCSP validator
     * @param ocspProperties The OCSP properties
     * @param sslBundles Security SSLBundles for truststore
     */
    public OcspTrustManagerFactory(CachedOcspValidator ocspValidator, OcspProperties ocspProperties, SslBundles sslBundles) {
        this.ocspValidator = ocspValidator;
        this.ocspProperties = ocspProperties;
        this.sslBundles = sslBundles;
    }

    /**
     * Creates a new OCSP-enabled trust manager using the default trust store.
     * 
     * @return The OCSP-enabled trust manager
     * @throws NoSuchAlgorithmException If the algorithm is not available
     * @throws KeyStoreException If there's an error accessing the key store
     */
    public TrustManager[] createTrustManagers() throws NoSuchAlgorithmException, KeyStoreException {
        return createTrustManagers(null);
    }

    /**
     * Creates a new OCSP-enabled trust manager using the specified trust store.
     * 
     * @param trustStore The trust store to use, or null for the default
     * @return The OCSP-enabled trust manager
     * @throws NoSuchAlgorithmException If the algorithm is not available
     * @throws KeyStoreException If there's an error accessing the key store
     */
    public TrustManager[] createTrustManagers(KeyStore trustStore) throws NoSuchAlgorithmException, KeyStoreException {
        // Create the default trust manager factory
    	TrustManager[] trustManagers = sslBundles.getBundle("connector").getManagers().getTrustManagers();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        
        // Create OCSP-enabled trust managers
        TrustManager[] ocspTrustManagers = new TrustManager[trustManagers.length];
        
        for (int i = 0; i < trustManagers.length; i++) {
            TrustManager tm = trustManagers[i];
            
            if (tm instanceof X509ExtendedTrustManager) {
                ocspTrustManagers[i] = new OcspX509TrustManager(
                        (X509ExtendedTrustManager) tm, 
                        ocspValidator, 
                        ocspProperties);
            } else {
                ocspTrustManagers[i] = tm;
            }
        }
        
        return ocspTrustManagers;
    }

    /**
     * Creates a new OCSP-enabled trust manager using the specified provider and algorithm.
     * 
     * @param provider The security provider
     * @param algorithm The algorithm
     * @param trustStore The trust store to use, or null for the default
     * @return The OCSP-enabled trust manager
     * @throws NoSuchAlgorithmException If the algorithm is not available
     * @throws KeyStoreException If there's an error accessing the key store
     */
    public TrustManager[] createTrustManagers(Provider provider, String algorithm, KeyStore trustStore) 
            throws NoSuchAlgorithmException, KeyStoreException {
        // Create the trust manager factory with the specified provider and algorithm
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm, provider);
        tmf.init(trustStore);
        
        // Get the trust managers
        TrustManager[] defaultTrustManagers = tmf.getTrustManagers();
        
        // Create OCSP-enabled trust managers
        TrustManager[] ocspTrustManagers = new TrustManager[defaultTrustManagers.length];
        
        for (int i = 0; i < defaultTrustManagers.length; i++) {
            TrustManager tm = defaultTrustManagers[i];
            
            if (tm instanceof X509ExtendedTrustManager) {
                ocspTrustManagers[i] = new OcspX509TrustManager(
                        (X509ExtendedTrustManager) tm, 
                        ocspValidator, 
                        ocspProperties);
            } else {
                ocspTrustManagers[i] = tm;
            }
        }
        
        return ocspTrustManagers;
    }
}
