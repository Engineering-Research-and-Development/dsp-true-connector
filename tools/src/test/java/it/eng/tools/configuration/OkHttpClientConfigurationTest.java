package it.eng.tools.configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;

import it.eng.tools.ssl.ocsp.OcspProperties;
import okhttp3.OkHttpClient;

@ExtendWith(MockitoExtension.class)
class OkHttpClientConfigurationTest {

    @InjectMocks
    private OkHttpClientConfiguration configuration;
    
    @Mock
    private OcspProperties ocspProperties;

    @Mock
    private SslBundles sslBundles;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should create OkHttpClient")
    void testOkHttpClient() throws KeyStoreException, NoSuchSslBundleException, KeyManagementException, NoSuchAlgorithmException {
        // Act
        OkHttpClient client = configuration.okHttpClient();

        // Assert
        assertNotNull(client);
        
        // Verify timeouts are set correctly
        assertEquals(60, getTimeoutMillis(client, "connect") / 1000);
        assertEquals(60, getTimeoutMillis(client, "write") / 1000);
        assertEquals(60, getTimeoutMillis(client, "read") / 1000);
    }
    
    @Test
    @DisplayName("Should handle SSL configuration")
    void testSslConfiguration() throws KeyStoreException, NoSuchSslBundleException, KeyManagementException, NoSuchAlgorithmException {
        // Act
        OkHttpClient client = configuration.okHttpClient();

        // Assert
        assertNotNull(client);
        assertNotNull(client.sslSocketFactory());
        
        // Verify hostname verifier is set (it's set to accept all hostnames in the insecure client)
        assertTrue(client.hostnameVerifier().verify("any-host", null));
    }
    
    private void assertEquals(long expected, long actual) {
        assertTrue(expected == actual, "Expected " + expected + " but was " + actual);
    }
    
    // Helper method to avoid direct access to private fields
    private long getTimeoutMillis(OkHttpClient client, String timeoutType) {
        if ("connect".equals(timeoutType)) {
            return client.connectTimeoutMillis();
        } else if ("write".equals(timeoutType)) {
            return client.writeTimeoutMillis();
        } else if ("read".equals(timeoutType)) {
            return client.readTimeoutMillis();
        }
        return 0;
    }
}
