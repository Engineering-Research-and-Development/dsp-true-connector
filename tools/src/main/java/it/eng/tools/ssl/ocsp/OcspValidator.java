package it.eng.tools.ssl.ocsp;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Validator for checking certificate revocation status using OCSP (Online Certificate Status Protocol).
 */
@Slf4j
public class OcspValidator {

    private static final int OCSP_TIMEOUT_SECONDS = 10;
    private static final String OCSP_REQUEST_TYPE = "application/ocsp-request";
    private static final String OCSP_RESPONSE_TYPE = "application/ocsp-response";

    /**
     * Validates a certificate using OCSP.
     * 
     * @param certificate The certificate to validate
     * @param issuerCertificate The issuer certificate
     * @return OcspValidationResult containing the validation result
     */
    public OcspValidationResult validate(X509Certificate certificate, X509Certificate issuerCertificate) {
        try {
            // Extract OCSP responder URI from certificate
            String ocspUrl = getOcspResponderUrl(certificate);
            if (ocspUrl == null) {
                log.warn("No OCSP responder URL found in certificate: {}", certificate.getSubjectX500Principal());
                return new OcspValidationResult(OcspValidationStatus.UNKNOWN, null, null, 
                        "No OCSP responder URL found in certificate");
            }
            
            // Create OCSP request
            OCSPReq request = buildOcspRequest(certificate, issuerCertificate);
            
            // Send request to OCSP responder
            OCSPResp response = sendOcspRequest(ocspUrl, request);
            
            // Process OCSP response
            return processOcspResponse(response, certificate, issuerCertificate);
            
        } catch (IOException e) {
            log.error("IO error during OCSP validation", e);
            return new OcspValidationResult(OcspValidationStatus.ERROR, null, null, 
                    "IO error: " + e.getMessage());
        } catch (OperatorCreationException | OCSPException | CertificateEncodingException e) {
            log.error("Error during OCSP validation", e);
            return new OcspValidationResult(OcspValidationStatus.ERROR, null, null, 
                    "Validation error: " + e.getMessage());
        }
    }

    /**
     * Extracts the OCSP responder URL from a certificate.
     * 
     * @param certificate The certificate to extract the OCSP responder URL from
     * @return The OCSP responder URL or null if not found
     */
    protected String getOcspResponderUrl(X509Certificate certificate) {
        try {
            byte[] authInfoAccessValue = certificate.getExtensionValue(Extension.authorityInfoAccess.getId());
            if (authInfoAccessValue == null) {
                return null;
            }
            
            ASN1Primitive derObject = ASN1Primitive.fromByteArray(authInfoAccessValue);
            if (derObject instanceof DEROctetString) {
                DEROctetString derOctetString = (DEROctetString) derObject;
                byte[] octets = derOctetString.getOctets();
                
                // Parse Authority Information Access extension
                org.bouncycastle.asn1.x509.AuthorityInformationAccess aia = 
                    org.bouncycastle.asn1.x509.AuthorityInformationAccess.getInstance(
                        ASN1Primitive.fromByteArray(octets));
                
                // Find OCSP access method
                org.bouncycastle.asn1.x509.AccessDescription[] accessDescriptions = aia.getAccessDescriptions();
                for (org.bouncycastle.asn1.x509.AccessDescription accessDescription : accessDescriptions) {
                    if (accessDescription.getAccessMethod().equals(org.bouncycastle.asn1.x509.AccessDescription.id_ad_ocsp)) {
                        org.bouncycastle.asn1.x509.GeneralName generalName = accessDescription.getAccessLocation();
                        if (generalName.getTagNo() == org.bouncycastle.asn1.x509.GeneralName.uniformResourceIdentifier) {
                            return generalName.getName().toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting OCSP responder URL", e);
        }
        return null;
    }

    /**
     * Builds an OCSP request for the given certificate.
     * 
     * @param certificate The certificate to check
     * @param issuerCertificate The issuer certificate
     * @return The OCSP request
     * @throws OperatorCreationException If there's an error creating the digest calculator
     * @throws CertificateEncodingException If there's an error encoding the certificates
     * @throws OCSPException If there's an error creating the OCSP request
     * @throws IOException If there's an IO error
     */
    protected OCSPReq buildOcspRequest(X509Certificate certificate, X509Certificate issuerCertificate) 
            throws OperatorCreationException, CertificateEncodingException, OCSPException, IOException {
        
        // Create digest calculator for SHA-1 (required for CertificateID)
        DigestCalculator digestCalculator = new JcaDigestCalculatorProviderBuilder()
                .build()
                .get(CertificateID.HASH_SHA1);
        
        // Create certificate ID for the request
        X509CertificateHolder issuerCertHolder = new JcaX509CertificateHolder(issuerCertificate);
        BigInteger serialNumber = certificate.getSerialNumber();
        CertificateID certificateId = new CertificateID(digestCalculator, issuerCertHolder, serialNumber);
        
        // Build the request
        OCSPReqBuilder builder = new OCSPReqBuilder();
        builder.addRequest(certificateId);
        
        // Add nonce extension for replay protection
        builder.setRequestExtensions(new Extensions(new Extension[] {
            new Extension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce, false, 
                    new DEROctetString(BigInteger.valueOf(System.currentTimeMillis()).toByteArray()))
        }));
        
        return builder.build();
    }

    /**
     * Sends an OCSP request to the specified URL.
     * 
     * @param url The OCSP responder URL
     * @param request The OCSP request
     * @return The OCSP response
     * @throws IOException If there's an IO error
     * @throws OCSPException If there's an error parsing the OCSP response
     */
    protected OCSPResp sendOcspRequest(String url, OCSPReq request) throws IOException, OCSPException {
        byte[] encodedRequest = request.getEncoded();
        
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(OCSP_TIMEOUT_SECONDS))
                .build();
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(OCSP_TIMEOUT_SECONDS))
                .header("Content-Type", OCSP_REQUEST_TYPE)
                .header("Accept", OCSP_RESPONSE_TYPE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(encodedRequest))
                .build();
        
        try {
            HttpResponse<byte[]> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            
            if (httpResponse.statusCode() != 200) {
                throw new IOException("OCSP responder returned error: HTTP " + httpResponse.statusCode());
            }
            
            return new OCSPResp(httpResponse.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OCSP request interrupted", e);
        }
    }

    /**
     * Processes an OCSP response.
     * 
     * @param ocspResponse The OCSP response
     * @param certificate The certificate that was checked
     * @param issuerCertificate The issuer certificate
     * @return The validation result
     * @throws OCSPException If there's an error processing the OCSP response
     * @throws OperatorCreationException If there's an error creating the digest calculator
     * @throws CertificateEncodingException If there's an error encoding the certificates
     * @throws IOException If there's an IO error
     */
    protected OcspValidationResult processOcspResponse(OCSPResp ocspResponse, X509Certificate certificate, 
            X509Certificate issuerCertificate) 
            throws OCSPException, OperatorCreationException, CertificateEncodingException, IOException {
        
        if (ocspResponse.getStatus() != OCSPResp.SUCCESSFUL) {
            return new OcspValidationResult(OcspValidationStatus.ERROR, null, null, 
                    "OCSP response error: " + ocspResponse.getStatus());
        }
        
        Object responseObject = ocspResponse.getResponseObject();
        if (!(responseObject instanceof BasicOCSPResp)) {
            return new OcspValidationResult(OcspValidationStatus.ERROR, null, null, 
                    "Unexpected OCSP response type: " + responseObject.getClass().getName());
        }
        
        BasicOCSPResp basicResponse = (BasicOCSPResp) responseObject;
        
        // Create certificate ID for matching the response
        DigestCalculator digestCalculator = new JcaDigestCalculatorProviderBuilder()
                .build()
                .get(CertificateID.HASH_SHA1);
        X509CertificateHolder issuerCertHolder = new JcaX509CertificateHolder(issuerCertificate);
        BigInteger serialNumber = certificate.getSerialNumber();
        CertificateID certificateId = new CertificateID(digestCalculator, issuerCertHolder, serialNumber);
        
        // Find matching response
        Optional<SingleResp> matchingResponse = List.of(basicResponse.getResponses())
                .stream()
                .filter(singleResp -> {
                    try {
                        return singleResp.getCertID().getSerialNumber().equals(certificateId.getSerialNumber());
                    } catch (Exception e) {
                        log.error("Error comparing certificate IDs", e);
                        return false;
                    }
                })
                .findFirst();
        
        if (matchingResponse.isEmpty()) {
            return new OcspValidationResult(OcspValidationStatus.ERROR, null, null, 
                    "No matching response found for certificate: " + certificate.getSubjectX500Principal());
        }
        
        SingleResp singleResp = matchingResponse.get();
        
        // Check if response is current
        Date thisUpdate = singleResp.getThisUpdate();
        Date nextUpdate = singleResp.getNextUpdate();
        
        Instant now = Instant.now();
        if (thisUpdate != null && thisUpdate.toInstant().isAfter(now)) {
            return new OcspValidationResult(OcspValidationStatus.ERROR, thisUpdate, nextUpdate, 
                    "OCSP response thisUpdate is in the future");
        }
        
        if (nextUpdate != null && nextUpdate.toInstant().isBefore(now)) {
            return new OcspValidationResult(OcspValidationStatus.ERROR, thisUpdate, nextUpdate, 
                    "OCSP response has expired");
        }
        
        // Check certificate status
        CertificateStatus status = singleResp.getCertStatus();
        
        if (status == null) {
            // null means good status
            return new OcspValidationResult(OcspValidationStatus.GOOD, thisUpdate, nextUpdate, null);
        } else if (status instanceof RevokedStatus) {
            RevokedStatus revokedStatus = (RevokedStatus) status;
            return new OcspValidationResult(OcspValidationStatus.REVOKED, thisUpdate, nextUpdate, 
                    "Certificate revoked at: " + revokedStatus.getRevocationTime());
        } else if (status instanceof UnknownStatus) {
            return new OcspValidationResult(OcspValidationStatus.UNKNOWN, thisUpdate, nextUpdate, 
                    "Certificate status unknown");
        } else {
            return new OcspValidationResult(OcspValidationStatus.ERROR, thisUpdate, nextUpdate, 
                    "Unexpected certificate status: " + status.getClass().getName());
        }
    }
}
