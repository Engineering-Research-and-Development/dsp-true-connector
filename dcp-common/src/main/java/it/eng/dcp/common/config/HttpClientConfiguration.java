package it.eng.dcp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dcp.common.client.SimpleOkHttpRestClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client configuration for DCP Issuer.
 * Creates OkHttpClient with TLS support for secure communications.
 */
@Configuration
@Slf4j
public class HttpClientConfiguration {

    private final boolean isSSLEnabled;
    private final SslBundles sslBundles;

    /**
     * Constructor.
     *
     * @param isSSLEnabled Whether SSL is enabled
     * @param sslBundles SSL bundles for TLS configuration
     */
    public HttpClientConfiguration(
            @Value("${server.ssl.enabled:false}") boolean isSSLEnabled,
            SslBundles sslBundles) {
        this.isSSLEnabled = isSSLEnabled;
        this.sslBundles = sslBundles;
    }

    /**
     * Creates OkHttpClient with TLS support.
     * For development (SSL disabled), creates insecure client that accepts all certificates.
     * For production (SSL enabled), creates secure client with proper certificate validation.
     *
     * @return Configured OkHttpClient
     * @throws KeyStoreException If there's an error accessing the key store
     * @throws NoSuchSslBundleException If SSL bundle is not found
     * @throws KeyManagementException If there's an error managing keys
     * @throws NoSuchAlgorithmException If the algorithm is not available
     */
    private OkHttpClient okHttpClient() throws KeyStoreException, NoSuchSslBundleException,
            KeyManagementException, NoSuchAlgorithmException {
        if (!isSSLEnabled) {
            log.warn("Creating insecure OkHttpClient (server.ssl.enabled=false)");
            log.warn("This client will accept ALL certificates without validation - use only in development!");
            return createInsecureClient();
        } else {
            log.info("Creating secure OkHttpClient with TLS validation (server.ssl.enabled=true)");
            return createSecureClient();
        }
    }

    /**
     * Creates SimpleOkHttpRestClient bean for DID resolution.
     * This is a minimal wrapper around OkHttpClient without dependencies on tools module.
     *
     * @return SimpleOkHttpRestClient for DID resolution
     * @throws KeyStoreException If there's an error accessing the key store
     * @throws NoSuchSslBundleException If SSL bundle is not found
     * @throws KeyManagementException If there's an error managing keys
     * @throws NoSuchAlgorithmException If the algorithm is not available
     */
    @Bean
    public SimpleOkHttpRestClient simpleOkHttpRestClient() throws KeyStoreException,
            NoSuchSslBundleException, KeyManagementException, NoSuchAlgorithmException {
        log.info("Creating SimpleOkHttpRestClient for DID resolution");
        return new SimpleOkHttpRestClient(okHttpClient(), new ObjectMapper());
    }

    /**
     * Creates a secure OkHttpClient with TLS validation.
     *
     * @return Secure OkHttpClient
     * @throws NoSuchAlgorithmException If the algorithm is not available
     * @throws KeyManagementException If there's an error managing keys
     */
    private OkHttpClient createSecureClient() throws NoSuchAlgorithmException,
            KeyManagementException {
        log.info("Creating secured OkHttpClient - remote certificates will be validated");

        // Get trust managers from SSL bundle
        TrustManager[] trustManagers = sslBundles.getBundle("connector").getManagers().getTrustManagers();
        log.debug("Created {} trust manager(s) from SSL bundle", trustManagers.length);

        if (trustManagers.length > 0 && trustManagers[0] instanceof X509TrustManager x509TrustManager) {
            int acceptedIssuersCount = x509TrustManager.getAcceptedIssuers() != null
                    ? x509TrustManager.getAcceptedIssuers().length : 0;
            log.info("Trust manager has {} accepted issuer(s) in truststore", acceptedIssuersCount);
        }

        // Create SSL context with trust managers
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, new java.security.SecureRandom());
        log.debug("SSLContext initialized with TLS protocol");

        // Build OkHttpClient with TLS
        OkHttpClient client = new OkHttpClient.Builder()
                .connectionSpecs(Collections.singletonList(ConnectionSpec.MODERN_TLS))
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0])
                .hostnameVerifier((hostname, session) -> true) // Accept all hostnames for now
                .build();

        log.info("Secure OkHttpClient created successfully");
        return client;
    }

    /**
     * Creates an insecure OkHttpClient that accepts all certificates without validation.
     * This should only be used in development or testing.
     *
     * @return Insecure OkHttpClient
     * @throws NoSuchAlgorithmException If the algorithm is not available
     * @throws KeyManagementException If there's an error managing keys
     */
    private OkHttpClient createInsecureClient() throws NoSuchAlgorithmException, KeyManagementException {
        log.warn("Creating NON SECURE OkHttpClient - This should only be used in development or testing");

        // Trust all certificates
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        // Build OkHttpClient that accepts all certificates and hostnames
        OkHttpClient client = new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .build();

        log.warn("Insecure OkHttpClient created - all certificates and hostnames will be accepted");
        return client;
    }
}

