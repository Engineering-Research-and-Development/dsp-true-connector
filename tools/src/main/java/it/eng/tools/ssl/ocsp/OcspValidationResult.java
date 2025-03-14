package it.eng.tools.ssl.ocsp;

import java.util.Date;

/**
 * Result of an OCSP validation check.
 */
public class OcspValidationResult {
    
    private final OcspValidationStatus status;
    private final Date thisUpdate;
    private final Date nextUpdate;
    private final String errorMessage;
    
    /**
     * Creates a new OCSP validation result.
     * 
     * @param status The validation status
     * @param thisUpdate The thisUpdate time from the OCSP response
     * @param nextUpdate The nextUpdate time from the OCSP response
     * @param errorMessage Error message if validation failed, null otherwise
     */
    public OcspValidationResult(OcspValidationStatus status, Date thisUpdate, Date nextUpdate, String errorMessage) {
        this.status = status;
        this.thisUpdate = thisUpdate;
        this.nextUpdate = nextUpdate;
        this.errorMessage = errorMessage;
    }
    
    /**
     * Gets the validation status.
     * 
     * @return The validation status
     */
    public OcspValidationStatus getStatus() {
        return status;
    }
    
    /**
     * Gets the thisUpdate time from the OCSP response.
     * 
     * @return The thisUpdate time
     */
    public Date getThisUpdate() {
        return thisUpdate;
    }
    
    /**
     * Gets the nextUpdate time from the OCSP response.
     * 
     * @return The nextUpdate time
     */
    public Date getNextUpdate() {
        return nextUpdate;
    }
    
    /**
     * Gets the error message if validation failed.
     * 
     * @return The error message or null if validation was successful
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Checks if the certificate is valid according to OCSP.
     * 
     * @return true if the certificate is valid, false otherwise
     */
    public boolean isValid() {
        return status == OcspValidationStatus.GOOD;
    }
}
