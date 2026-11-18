package it.eng.tools.configuration;

import it.eng.tools.ssl.ocsp.OcspTrustManagerFactory;
import okhttp3.OkHttpClient;

import java.security.GeneralSecurityException;

/**
 * Builds the shared {@code okHttpClient} bean the way {@link OkHttpClientConfiguration} does, for tests in other
 * packages.
 */
public final class SharedOkHttpClients {

    private SharedOkHttpClients() {
    }

    /**
     * Builds the shared OkHttp client.
     *
     * @param ocspTrustManagerFactory supplies the trust managers when {@code sslEnabled} is {@code true}
     * @param sslEnabled the value of {@code server.ssl.enabled}
     * @return the shared OkHttp client
     * @throws GeneralSecurityException when the TLS context cannot be created
     */
    public static OkHttpClient sharedClient(OcspTrustManagerFactory ocspTrustManagerFactory, boolean sslEnabled)
            throws GeneralSecurityException {
        return new OkHttpClientConfiguration(ocspTrustManagerFactory, sslEnabled).okHttpClient();
    }
}
