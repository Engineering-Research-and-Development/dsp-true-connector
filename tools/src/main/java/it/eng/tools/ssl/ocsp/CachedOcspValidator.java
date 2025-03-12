package it.eng.tools.ssl.ocsp;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

/**
 * OCSP validator with caching capabilities to improve performance and handle OCSP responder unavailability.
 */
@Component
@Slf4j
public class CachedOcspValidator {

    private final OcspValidator ocspValidator;
    private final Cache<CertificateId, OcspValidationResult> responseCache;
    private final boolean softFail;
    private final long defaultCacheDurationMinutes;

    /**
     * Creates a new cached OCSP validator.
     * 
     * @param softFail Whether to soft-fail (allow connection) when OCSP validation fails
     * @param defaultCacheDurationMinutes Default cache duration in minutes for responses without nextUpdate
     */
    public CachedOcspValidator(
            @Value("${ocsp.validation.soft-fail:true}") boolean softFail,
            @Value("${ocsp.validation.default-cache-duration-minutes:60}") long defaultCacheDurationMinutes) {
        this.ocspValidator = new OcspValidator();
        this.softFail = softFail;
        this.defaultCacheDurationMinutes = defaultCacheDurationMinutes;
        
        // Initialize cache with size-based eviction and weak references
        this.responseCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(24, TimeUnit.HOURS) // Maximum cache time as a safety measure
                .build();
    }

    /**
     * Validates a certificate using OCSP with caching.
     * 
     * @param certificate The certificate to validate
     * @param issuerCertificate The issuer certificate
     * @return true if the certificate is valid or if soft-fail is enabled and validation failed, false otherwise
     */
    public boolean validate(X509Certificate certificate, X509Certificate issuerCertificate) {
        if (certificate == null || issuerCertificate == null) {
            log.warn("Certificate or issuer certificate is null");
            return softFail;
        }

        try {
            CertificateId certId = new CertificateId(certificate, issuerCertificate);
            
            // Try to get from cache first
            OcspValidationResult cachedResult = responseCache.getIfPresent(certId);
            // Check if cached result is still valid
            if (cachedResult != null && !isCacheExpired(cachedResult)) {
                log.debug("Using cached OCSP validation result for: {}", certificate.getSubjectX500Principal());
                return cachedResult.isValid() || softFail;
            }
            
            // Perform actual validation
            OcspValidationResult result = ocspValidator.validate(certificate, issuerCertificate);
            
            // Cache the result if it's not an error
            if (result.getStatus() != OcspValidationStatus.ERROR) {
                cacheResult(certId, result);
            }
            
            return result.isValid() || softFail;
            
        } catch (Exception e) {
            log.error("Error during cached OCSP validation", e);
            return softFail;
        }
    }

    /**
     * Caches an OCSP validation result.
     * 
     * @param certId The certificate ID
     * @param result The validation result
     */
    private void cacheResult(CertificateId certId, OcspValidationResult result) {
        responseCache.put(certId, result);
    }

    /**
     * Checks if a cached result has expired.
     * 
     * @param result The cached result
     * @return true if the result has expired, false otherwise
     */
    private boolean isCacheExpired(OcspValidationResult result) {
        Date nextUpdate = result.getNextUpdate();
        
        if (nextUpdate != null) {
            // Use nextUpdate from OCSP response
            return Instant.now().isAfter(nextUpdate.toInstant());
        } else {
            // Use default cache duration
            Date thisUpdate = result.getThisUpdate();
            if (thisUpdate != null) {
                Instant expiryTime = thisUpdate.toInstant().plus(Duration.ofMinutes(defaultCacheDurationMinutes));
                return Instant.now().isAfter(expiryTime);
            }
        }
        
        // If no timing information is available, consider it expired
        return true;
    }

    /**
     * Clears the cache.
     */
    public void clearCache() {
        responseCache.invalidateAll();
    }

    /**
     * Record class for certificate identification in the cache.
     */
    private record CertificateId(byte[] issuerNameHash, byte[] issuerKeyHash, byte[] serialNumber) {
        
        /**
         * Creates a new certificate ID from certificates.
         * 
         * @param certificate The certificate
         * @param issuerCertificate The issuer certificate
         */
        public CertificateId(X509Certificate certificate, X509Certificate issuerCertificate) {
            this(
                issuerCertificate.getSubjectX500Principal().getEncoded(),
                issuerCertificate.getPublicKey().getEncoded(),
                certificate.getSerialNumber().toByteArray()
            );
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CertificateId that = (CertificateId) o;
            return java.util.Arrays.equals(issuerNameHash, that.issuerNameHash) &&
                   java.util.Arrays.equals(issuerKeyHash, that.issuerKeyHash) &&
                   java.util.Arrays.equals(serialNumber, that.serialNumber);
        }
        
        @Override
        public int hashCode() {
            int result = Objects.hash();
            result = 31 * result + java.util.Arrays.hashCode(issuerNameHash);
            result = 31 * result + java.util.Arrays.hashCode(issuerKeyHash);
            result = 31 * result + java.util.Arrays.hashCode(serialNumber);
            return result;
        }
    }
}
