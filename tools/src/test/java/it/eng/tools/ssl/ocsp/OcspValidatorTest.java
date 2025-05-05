package it.eng.tools.ssl.ocsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OcspValidatorTest {

    @Spy
    @InjectMocks
    private OcspValidator ocspValidator;

    private X509Certificate certificate;
    private X509Certificate issuerCertificate;
    private PrivateKey issuerPrivateKey;

    @BeforeEach
    void setUp() throws Exception {
        // Generate test certificates
        KeyPair issuerKeyPair = generateKeyPair();
        KeyPair subjectKeyPair = generateKeyPair();
        
        issuerPrivateKey = issuerKeyPair.getPrivate();
        
        issuerCertificate = generateCertificate(
                "CN=Test CA", 
                "CN=Test CA", 
                issuerKeyPair.getPublic(), 
                issuerPrivateKey, 
                true, 
                null);
        
        certificate = generateCertificate(
                "CN=Test Subject", 
                "CN=Test CA", 
                subjectKeyPair.getPublic(), 
                issuerPrivateKey, 
                false, 
                "http://ocsp.example.com");
    }

    @Test
    @DisplayName("Should extract OCSP responder URL from certificate")
    void testGetOcspResponderUrl() {
        // Act
        String url = ocspValidator.getOcspResponderUrl(certificate);
        
        // Assert
        assertEquals("http://ocsp.example.com", url);
    }

    @Test
    @DisplayName("Should return null when certificate has no OCSP responder URL")
    void testGetOcspResponderUrlNoUrl() throws Exception {
        // Arrange
        KeyPair keyPair = generateKeyPair();
        X509Certificate certWithoutOcsp = generateCertificate(
                "CN=No OCSP", 
                "CN=Test CA", 
                keyPair.getPublic(), 
                issuerPrivateKey, 
                false, 
                null);
        
        // Act
        String url = ocspValidator.getOcspResponderUrl(certWithoutOcsp);
        
        // Assert
        assertNotNull(certWithoutOcsp);
        assertEquals(null, url);
    }

    @Test
    @DisplayName("Should build valid OCSP request")
    void testBuildOcspRequest() throws Exception {
        // Act
        OCSPReq request = ocspValidator.buildOcspRequest(certificate, issuerCertificate);
        
        // Assert
        assertNotNull(request);
        assertTrue(request.getRequestList().length > 0);
        assertEquals(certificate.getSerialNumber(), request.getRequestList()[0].getCertID().getSerialNumber());
    }

    @Test
    @DisplayName("Should process OCSP response with good status")
    void testProcessOcspResponseGood() throws Exception {
        // Arrange
        OCSPResp response = createMockOcspResponse(OCSPResp.SUCCESSFUL, null);
        
        // Act
        OcspValidationResult result = ocspValidator.processOcspResponse(response, certificate, issuerCertificate);
        
        // Assert
        assertNotNull(result);
        assertEquals(OcspValidationStatus.GOOD, result.getStatus());
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should process OCSP response with revoked status")
    void testProcessOcspResponseRevoked() throws Exception {
        // Arrange
        RevokedStatus revokedStatus = new RevokedStatus(new Date(), 0);
        OCSPResp response = createMockOcspResponse(OCSPResp.SUCCESSFUL, revokedStatus);
        
        // Act
        OcspValidationResult result = ocspValidator.processOcspResponse(response, certificate, issuerCertificate);
        
        // Assert
        assertNotNull(result);
        assertEquals(OcspValidationStatus.REVOKED, result.getStatus());
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Should process OCSP response with unknown status")
    void testProcessOcspResponseUnknown() throws Exception {
        // Arrange
        UnknownStatus unknownStatus = new UnknownStatus();
        OCSPResp response = createMockOcspResponse(OCSPResp.SUCCESSFUL, unknownStatus);
        
        // Act
        OcspValidationResult result = ocspValidator.processOcspResponse(response, certificate, issuerCertificate);
        
        // Assert
        assertNotNull(result);
        assertEquals(OcspValidationStatus.UNKNOWN, result.getStatus());
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Should handle error in OCSP response")
    void testProcessOcspResponseError() throws Exception {
        // Arrange
        OCSPResp response = createMockOcspResponse(OCSPResp.INTERNAL_ERROR, null);
        
        // Act
        OcspValidationResult result = ocspValidator.processOcspResponse(response, certificate, issuerCertificate);
        
        // Assert
        assertNotNull(result);
        assertEquals(OcspValidationStatus.ERROR, result.getStatus());
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Should handle IO exception during OCSP validation")
    void testValidateIOException() throws Exception {
        // Arrange
        Mockito.doReturn("http://ocsp.example.com").when(ocspValidator).getOcspResponderUrl(any());
        Mockito.doThrow(new IOException("Test IO exception")).when(ocspValidator).sendOcspRequest(any(), any());
        
        // Act
        OcspValidationResult result = ocspValidator.validate(certificate, issuerCertificate);
        
        // Assert
        assertNotNull(result);
        assertEquals(OcspValidationStatus.ERROR, result.getStatus());
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Should handle missing OCSP responder URL")
    void testValidateMissingResponderUrl() throws Exception {
        // Arrange
        Mockito.doReturn(null).when(ocspValidator).getOcspResponderUrl(any());
        
        // Act
        OcspValidationResult result = ocspValidator.validate(certificate, issuerCertificate);
        
        // Assert
        assertNotNull(result);
        assertEquals(OcspValidationStatus.UNKNOWN, result.getStatus());
        assertFalse(result.isValid());
    }

    // Helper methods

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    private X509Certificate generateCertificate(String subjectDN, String issuerDN, PublicKey publicKey, 
            PrivateKey privateKey, boolean isCA, String ocspResponderUrl) throws Exception {
        
        X500Name subject = new X500Name(subjectDN);
        X500Name issuer = new X500Name(issuerDN);
        
        BigInteger serialNumber = BigInteger.valueOf(System.currentTimeMillis());
        Instant startDate = Instant.now();
        Instant endDate = startDate.plus(365, ChronoUnit.DAYS);
        
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serialNumber,
                Date.from(startDate),
                Date.from(endDate),
                subject,
                publicKey);
        
        // Add Basic Constraints for CA
        if (isCA) {
            certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        }
        
        // Add OCSP responder URL if provided
        if (ocspResponderUrl != null) {
            GeneralName ocspName = new GeneralName(GeneralName.uniformResourceIdentifier, ocspResponderUrl);
            AuthorityInformationAccess aia = new AuthorityInformationAccess(
                    new ASN1ObjectIdentifier("1.3.6.1.5.5.7.48.1"), // OCSP
                    ocspName);
            certBuilder.addExtension(Extension.authorityInfoAccess, false, aia);
        }
        
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        X509CertificateHolder certHolder = certBuilder.build(contentSigner);
        
        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }

    private OCSPResp createMockOcspResponse(int status, CertificateStatus certStatus) throws Exception {
        if (status != OCSPResp.SUCCESSFUL) {
            // For non-successful responses, create a mock directly
            OCSPResp mockResponse = mock(OCSPResp.class);
            when(mockResponse.getStatus()).thenReturn(status);
            return mockResponse;
        }
        
        // Create mock BasicOCSPResp
        BasicOCSPResp mockBasicResponse = mock(BasicOCSPResp.class);
        
        // Create mock SingleResp
        SingleResp mockSingleResp = mock(SingleResp.class);
        when(mockSingleResp.getCertStatus()).thenReturn(certStatus);
        when(mockSingleResp.getThisUpdate()).thenReturn(new Date());
        when(mockSingleResp.getNextUpdate()).thenReturn(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
        
        // Create mock CertificateID
        CertificateID mockCertId = mock(CertificateID.class);
        when(mockCertId.getSerialNumber()).thenReturn(certificate.getSerialNumber());
        when(mockSingleResp.getCertID()).thenReturn(mockCertId);
        
        // Set up BasicOCSPResp
        when(mockBasicResponse.getResponses()).thenReturn(new SingleResp[] { mockSingleResp });
        
        // Create OCSPResp with the mock BasicOCSPResp
        OCSPResp mockResponse = mock(OCSPResp.class);
        when(mockResponse.getStatus()).thenReturn(status);
        when(mockResponse.getResponseObject()).thenReturn(mockBasicResponse);
        
        return mockResponse;
    }
}
