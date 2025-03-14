package it.eng.tools.ssl.ocsp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class OcspX509TrustManagerTest {

    @Mock
    private X509ExtendedTrustManager delegateTrustManager;
    
    @Mock
    private CachedOcspValidator ocspValidator;
    
    @Mock
    private OcspProperties ocspProperties;
    
    private X509Certificate[] chain;
    
    @Mock
    private SSLEngine engine;
    
    private OcspX509TrustManager trustManager;

    @BeforeEach
    void setUp() {
        // Create real array with mock certificates instead of mocking the array
        X509Certificate mockCert = org.mockito.Mockito.mock(X509Certificate.class);
        X509Certificate mockIssuer = org.mockito.Mockito.mock(X509Certificate.class);
        chain = new X509Certificate[] { mockCert, mockIssuer };
        
        trustManager = new OcspX509TrustManager(delegateTrustManager, ocspValidator, ocspProperties);
    }

    @Test
    @DisplayName("Should delegate to underlying trust manager when OCSP is disabled")
    void testCheckServerTrustedOcspDisabled() throws CertificateException {
        // Arrange
        when(ocspProperties.isEnabled()).thenReturn(false);
        doNothing().when(delegateTrustManager).checkServerTrusted(any(), any());
        
        // Act & Assert
        assertDoesNotThrow(() -> trustManager.checkServerTrusted(chain, "RSA"));
        
        // Verify
        verify(delegateTrustManager, times(1)).checkServerTrusted(chain, "RSA");
        verify(ocspValidator, times(0)).validate(any(), any());
    }

    @Test
    @DisplayName("Should validate with OCSP when enabled")
    void testCheckServerTrustedOcspEnabled() throws CertificateException {
        // Arrange
        when(ocspProperties.isEnabled()).thenReturn(true);
        doNothing().when(delegateTrustManager).checkServerTrusted(any(), any());
        when(ocspValidator.validate(any(), any())).thenReturn(true);
        
        // Act & Assert
        assertDoesNotThrow(() -> trustManager.checkServerTrusted(chain, "RSA"));
        
        // Verify
        verify(delegateTrustManager, times(1)).checkServerTrusted(chain, "RSA");
    }

    @Test
    @DisplayName("Should throw exception when OCSP validation fails and soft-fail is disabled")
    void testCheckServerTrustedOcspFailureHardFail() throws CertificateException {
        // Arrange
        when(ocspProperties.isEnabled()).thenReturn(true);
        when(ocspProperties.isSoftFail()).thenReturn(false);
        doNothing().when(delegateTrustManager).checkServerTrusted(any(), any());
        when(ocspValidator.validate(any(), any())).thenReturn(false);
        
        // Mock a non-empty chain
        X509Certificate cert = org.mockito.Mockito.mock(X509Certificate.class);
        X509Certificate issuer = org.mockito.Mockito.mock(X509Certificate.class);
        X509Certificate[] mockChain = new X509Certificate[] { cert, issuer };
        
        // Act & Assert
        assertThrows(CertificateException.class, () -> trustManager.checkServerTrusted(mockChain, "RSA"));
        
        // Verify
        verify(delegateTrustManager, times(1)).checkServerTrusted(mockChain, "RSA");
    }

    @Test
    @DisplayName("Should not throw exception when OCSP validation fails but soft-fail is enabled")
    void testCheckServerTrustedOcspFailureSoftFail() throws CertificateException {
        // Arrange
        when(ocspProperties.isEnabled()).thenReturn(true);
        when(ocspProperties.isSoftFail()).thenReturn(true);
        doNothing().when(delegateTrustManager).checkServerTrusted(any(), any());
        when(ocspValidator.validate(any(), any())).thenReturn(false);
        
        // Mock a non-empty chain
        X509Certificate cert = org.mockito.Mockito.mock(X509Certificate.class);
        X509Certificate issuer = org.mockito.Mockito.mock(X509Certificate.class);
        X509Certificate[] mockChain = new X509Certificate[] { cert, issuer };
        
        // Act & Assert
        assertDoesNotThrow(() -> trustManager.checkServerTrusted(mockChain, "RSA"));
        
        // Verify
        verify(delegateTrustManager, times(1)).checkServerTrusted(mockChain, "RSA");
    }

    @Test
    @DisplayName("Should handle exceptions from delegate trust manager")
    void testCheckServerTrustedDelegateException() throws CertificateException {
        // Arrange
        when(ocspProperties.isEnabled()).thenReturn(true);
        doThrow(new CertificateException("Test exception")).when(delegateTrustManager).checkServerTrusted(chain, "RSA");
        
        // Act & Assert
        assertThrows(CertificateException.class, () -> trustManager.checkServerTrusted(chain, "RSA"));
        
        // Verify
        verify(delegateTrustManager, times(1)).checkServerTrusted(chain, "RSA");
        verify(ocspValidator, times(0)).validate(any(), any());
    }

    @Test
    @DisplayName("Should handle empty certificate chain")
    void testCheckServerTrustedEmptyChain() throws CertificateException {
        // Arrange
        when(ocspProperties.isEnabled()).thenReturn(true);
        when(ocspProperties.isSoftFail()).thenReturn(false);
        doNothing().when(delegateTrustManager).checkServerTrusted(any(), any());
        
        // Empty chain
        X509Certificate[] emptyChain = new X509Certificate[0];
        
        // Act & Assert
        assertThrows(CertificateException.class, () -> trustManager.checkServerTrusted(emptyChain, "RSA"));
        
        // Verify
        verify(delegateTrustManager, times(1)).checkServerTrusted(emptyChain, "RSA");
    }

    @Test
    @DisplayName("Should handle exceptions during OCSP validation")
    void testCheckServerTrustedOcspException() throws CertificateException {
        // Arrange
        when(ocspProperties.isEnabled()).thenReturn(true);
        when(ocspProperties.isSoftFail()).thenReturn(false);
        doNothing().when(delegateTrustManager).checkServerTrusted(any(), any());
        when(ocspValidator.validate(any(), any())).thenThrow(new RuntimeException("Test exception"));
        
        // Mock a non-empty chain
        X509Certificate cert = org.mockito.Mockito.mock(X509Certificate.class);
        X509Certificate issuer = org.mockito.Mockito.mock(X509Certificate.class);
        X509Certificate[] mockChain = new X509Certificate[] { cert, issuer };
        
        // Act & Assert
        assertThrows(CertificateException.class, () -> trustManager.checkServerTrusted(mockChain, "RSA"));
        
        // Verify
        verify(delegateTrustManager, times(1)).checkServerTrusted(mockChain, "RSA");
    }

    @Test
    @DisplayName("Should delegate getAcceptedIssuers to underlying trust manager")
    void testGetAcceptedIssuers() {
        // Arrange
        X509Certificate[] issuers = new X509Certificate[0];
        when(delegateTrustManager.getAcceptedIssuers()).thenReturn(issuers);
        
        // Act
        X509Certificate[] result = trustManager.getAcceptedIssuers();
        
        // Assert
        verify(delegateTrustManager, times(1)).getAcceptedIssuers();
    }
}
