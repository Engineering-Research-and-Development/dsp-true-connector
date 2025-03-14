package it.eng.tools.ssl.ocsp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CachedOcspValidatorTest {

    @Mock
    private OcspValidator ocspValidator;

    private CachedOcspValidator cachedOcspValidator;
    
    @Mock
    private X509Certificate certificate;
    
    @Mock
    private X509Certificate issuerCertificate;

    @BeforeEach
    void setUp() {
        cachedOcspValidator = new CachedOcspValidator(true, 60);
        ReflectionTestUtils.setField(cachedOcspValidator, "ocspValidator", ocspValidator);
        
        // Mock certificate methods to avoid NullPointerException - use lenient to avoid unnecessary stubbing errors
        javax.security.auth.x500.X500Principal mockPrincipal = mock(javax.security.auth.x500.X500Principal.class);
        Mockito.lenient().when(mockPrincipal.getEncoded()).thenReturn(new byte[] { 1, 2, 3 });
        
        Mockito.lenient().when(certificate.getSubjectX500Principal()).thenReturn(mockPrincipal);
        Mockito.lenient().when(certificate.getSerialNumber()).thenReturn(java.math.BigInteger.ONE);
        
        Mockito.lenient().when(issuerCertificate.getSubjectX500Principal()).thenReturn(mockPrincipal);
        Mockito.lenient().when(issuerCertificate.getPublicKey()).thenReturn(mock(java.security.PublicKey.class));
    }

    @Test
    @DisplayName("Should return true when certificate is valid")
    void testValidateValidCertificate() {
        // Arrange
        OcspValidationResult validResult = new OcspValidationResult(
                OcspValidationStatus.GOOD, 
                new Date(), 
                Date.from(Instant.now().plusSeconds(3600)), 
                null);
        
        when(ocspValidator.validate(any(), any())).thenReturn(validResult);
        
        // Act
        boolean result = cachedOcspValidator.validate(certificate, issuerCertificate);
        
        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Should return false when certificate is revoked and soft-fail is disabled")
    void testValidateRevokedCertificateHardFail() {
        // Arrange
        cachedOcspValidator = new CachedOcspValidator(false, 60);
        ReflectionTestUtils.setField(cachedOcspValidator, "ocspValidator", ocspValidator);
        
        OcspValidationResult revokedResult = new OcspValidationResult(
                OcspValidationStatus.REVOKED, 
                new Date(), 
                Date.from(Instant.now().plusSeconds(3600)), 
                "Certificate revoked");
        
        when(ocspValidator.validate(any(), any())).thenReturn(revokedResult);
        
        // Act
        boolean result = cachedOcspValidator.validate(certificate, issuerCertificate);
        
        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Should return true when certificate is revoked but soft-fail is enabled")
    void testValidateRevokedCertificateSoftFail() {
        // Arrange
        OcspValidationResult revokedResult = new OcspValidationResult(
                OcspValidationStatus.REVOKED, 
                new Date(), 
                Date.from(Instant.now().plusSeconds(3600)), 
                "Certificate revoked");
        
        when(ocspValidator.validate(any(), any())).thenReturn(revokedResult);
        
        // Act
        boolean result = cachedOcspValidator.validate(certificate, issuerCertificate);
        
        // Assert
        assertTrue(result);
    }

    @Test
    @DisplayName("Should return cached result when available")
    void testValidateCachedResult() {
        // Arrange
        OcspValidationResult validResult = new OcspValidationResult(
                OcspValidationStatus.GOOD, 
                new Date(), 
                Date.from(Instant.now().plusSeconds(3600)), 
                null);
        
        when(ocspValidator.validate(any(), any())).thenReturn(validResult);
        
        // Act - First call should cache the result
        boolean firstResult = cachedOcspValidator.validate(certificate, issuerCertificate);
        
        // Second call should use cached result
        boolean secondResult = cachedOcspValidator.validate(certificate, issuerCertificate);
        
        // Assert
        assertTrue(firstResult);
        assertTrue(secondResult);
        
        // Verify that the validator was only called once
        verify(ocspValidator, times(1)).validate(any(), any());
    }

    @Test
    @DisplayName("Should handle null certificates")
    void testValidateNullCertificates() {
        // Act
        boolean result = cachedOcspValidator.validate(null, null);
        
        // Assert
        assertTrue(result); // Should return true because soft-fail is enabled
        
        // Verify that the validator was not called
        verify(ocspValidator, times(0)).validate(any(), any());
    }

    @Test
    @DisplayName("Should handle exceptions during validation")
    void testValidateException() {
        // Arrange
        when(ocspValidator.validate(any(), any())).thenThrow(new RuntimeException("Test exception"));
        
        // Act
        boolean result = cachedOcspValidator.validate(certificate, issuerCertificate);
        
        // Assert
        assertTrue(result); // Should return true because soft-fail is enabled
    }

    @Test
    @DisplayName("Should clear cache when requested")
    void testClearCache() {
        // Arrange
        OcspValidationResult validResult = new OcspValidationResult(
                OcspValidationStatus.GOOD, 
                new Date(), 
                Date.from(Instant.now().plusSeconds(3600)), 
                null);
        
        when(ocspValidator.validate(any(), any())).thenReturn(validResult);
        
        // Act - First call should cache the result
        cachedOcspValidator.validate(certificate, issuerCertificate);
        
        // Clear the cache
        cachedOcspValidator.clearCache();
        
        // Second call should not use cached result
        cachedOcspValidator.validate(certificate, issuerCertificate);
        
        // Assert - Verify that the validator was called twice
        verify(ocspValidator, times(2)).validate(any(), any());
    }
}
