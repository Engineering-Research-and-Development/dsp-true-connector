package it.eng.tools.ssl.ocsp;

/**
 * Status of an OCSP validation check.
 */
public enum OcspValidationStatus {
    /**
     * Certificate is valid.
     */
    GOOD,
    
    /**
     * Certificate has been revoked.
     */
    REVOKED,
    
    /**
     * Certificate status is unknown.
     */
    UNKNOWN,
    
    /**
     * Error occurred during validation.
     */
    ERROR
}
