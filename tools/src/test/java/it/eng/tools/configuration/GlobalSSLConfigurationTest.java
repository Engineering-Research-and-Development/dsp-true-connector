package it.eng.tools.configuration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleKey;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslStoreBundle;

import javax.net.ssl.HttpsURLConnection;

@ExtendWith(MockitoExtension.class)
class GlobalSSLConfigurationTest {

    @Mock
    private SslBundles sslBundles;
    
    @Mock
    private SslBundle sslBundle;
    
    @Mock
    private SslStoreBundle storeBundle;
    
    @Mock
    private KeyStore keyStore;
    
    @Mock
    private SslBundleKey bundleKey;
    
    @Mock
    private SSLContext sslContext;
    
    @Mock
    private Certificate certificate;
    
    @Mock
    private PublicKey publicKey;
    
    @Mock
    private PrivateKey privateKey;
    
    @Mock
    private SSLSocketFactory socketFactory;

    private GlobalSSLConfiguration configuration;

    @BeforeEach
    void setUp() throws NoSuchSslBundleException, KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
        // Setup mocks with lenient stubbings to avoid UnnecessaryStubbingException
        Mockito.lenient().when(sslBundles.getBundle("connector")).thenReturn(sslBundle);
        Mockito.lenient().when(sslBundle.createSslContext()).thenReturn(sslContext);
        Mockito.lenient().when(sslContext.getSocketFactory()).thenReturn(socketFactory);
        Mockito.lenient().when(sslBundle.getStores()).thenReturn(storeBundle);
        Mockito.lenient().when(storeBundle.getKeyStore()).thenReturn(keyStore);
        Mockito.lenient().when(sslBundle.getKey()).thenReturn(bundleKey);
        Mockito.lenient().when(bundleKey.getAlias()).thenReturn("execution-core-container");
        Mockito.lenient().when(bundleKey.getPassword()).thenReturn("password");
        Mockito.lenient().when(keyStore.getCertificate(anyString())).thenReturn(certificate);
        Mockito.lenient().when(certificate.getPublicKey()).thenReturn(publicKey);
        Mockito.lenient().when(keyStore.getKey(anyString(), any(char[].class))).thenReturn(privateKey);
        
        // Create configuration instance after mocks are set up
        configuration = new GlobalSSLConfiguration(sslBundles);
    }

    @Test
    @DisplayName("Should initialize SSL configuration")
    void testGlobalSslConfig() {
        // Use MockedStatic to mock the static method HttpsURLConnection.setDefaultSSLSocketFactory
        try (MockedStatic<HttpsURLConnection> mockedStatic = Mockito.mockStatic(HttpsURLConnection.class)) {
            // Act
            configuration.globalSslConfig();
            
            // Assert
            verify(sslBundles, Mockito.atLeastOnce()).getBundle("connector");
            verify(sslBundle).createSslContext();
            mockedStatic.verify(() -> HttpsURLConnection.setDefaultSSLSocketFactory(any(SSLSocketFactory.class)));
        }
    }
    
    @Test
    @DisplayName("Should handle exceptions during key loading")
    void testKeyLoadingExceptions() throws KeyStoreException, UnrecoverableKeyException, NoSuchAlgorithmException {
        // Arrange
        when(keyStore.getCertificate(anyString())).thenThrow(new KeyStoreException("Test exception"));
        
        // Use MockedStatic to mock the static method HttpsURLConnection.setDefaultSSLSocketFactory
        try (MockedStatic<HttpsURLConnection> mockedStatic = Mockito.mockStatic(HttpsURLConnection.class)) {
            // Act
            configuration.globalSslConfig();
            
            // Assert - should not throw exception but log error
            verify(sslBundles, Mockito.atLeastOnce()).getBundle("connector");
            mockedStatic.verify(() -> HttpsURLConnection.setDefaultSSLSocketFactory(any(SSLSocketFactory.class)));
        }
    }
    
    // No longer needed as we're now importing ArgumentMatchers.any directly
    // private static <T> T any(Class<T> type) {
    //     return org.mockito.ArgumentMatchers.any(type);
    // }
}
