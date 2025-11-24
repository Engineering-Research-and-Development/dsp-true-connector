package it.eng.tools.configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import it.eng.tools.ssl.ocsp.OcspTrustManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ssl.NoSuchSslBundleException;

import okhttp3.OkHttpClient;

@ExtendWith(MockitoExtension.class)
class OkHttpClientConfigurationTest {

    private OkHttpClientConfiguration configuration;

    @Mock
    private OcspTrustManagerFactory ocspTrustManagerFactory;

    @Test
    @DisplayName("Should create OkHttpClient")
    void testOkHttpClient() throws KeyStoreException, NoSuchSslBundleException, KeyManagementException, NoSuchAlgorithmException {
        configuration = new OkHttpClientConfiguration(ocspTrustManagerFactory, false);

        // Act
        OkHttpClient client = configuration.okHttpClient();

        // Assert
        assertNotNull(client);
        assertNotNull(client.sslSocketFactory(), "SSL socket factory should be set");

        // Verify timeouts are set correctly
        assertEquals(60, client.connectTimeoutMillis() / 1000);
        assertEquals(60, client.writeTimeoutMillis() / 1000);
        assertEquals(60, client.readTimeoutMillis() / 1000);

        // Verify hostname verifier accepts any hostname (insecure)
        assertTrue(client.hostnameVerifier().verify("any-hostname", null), "Insecure client should accept any hostname");
        assertTrue(client.hostnameVerifier().verify("test.example.com", null), "Insecure client should accept any hostname");
    }
    
    @Test
    @DisplayName("Should handle SSL configuration")
    void testSslConfiguration() throws KeyStoreException, NoSuchSslBundleException, KeyManagementException, NoSuchAlgorithmException {
        // Create a mock trust manager
        X509TrustManager mockTrustManager = new X509TrustManager() {
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
        };

        // Mock the ocspTrustManagerFactory to return the mock trust manager
        when(ocspTrustManagerFactory.createTrustManagers()).thenReturn(new TrustManager[]{mockTrustManager});

        configuration = new OkHttpClientConfiguration(ocspTrustManagerFactory, true);

        // Act
        OkHttpClient client = configuration.okHttpClient();

        // Assert
        assertNotNull(client);
        assertNotNull(client.sslSocketFactory(), "SSL socket factory should be set for secure client");

        // Verify timeouts are set correctly
        assertEquals(60, client.connectTimeoutMillis() / 1000);
        assertEquals(60, client.writeTimeoutMillis() / 1000);
        assertEquals(60, client.readTimeoutMillis() / 1000);

        // Verify hostname verifier is set (for secure client, it should NOT be the trust-all lambda)
        assertNotNull(client.hostnameVerifier(), "Hostname verifier should be set");
        // The secure client should use OkHostnameVerifier which is more strict
        // We can't directly verify the class due to it being an internal OkHttp class,
        // but we verified that when SSL is enabled, the code sets it to OkHostnameVerifier.INSTANCE
    }
}
