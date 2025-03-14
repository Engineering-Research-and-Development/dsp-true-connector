package it.eng.tools.ssl.ocsp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration properties for OCSP validation.
 */
@Configuration
@ConfigurationProperties(prefix = "application.ocsp.validation")
@Data
public class OcspProperties {
    
    /**
     * Whether to enable OCSP validation.
     */
    private boolean enabled;// = true;
    
    /**
     * Whether to soft-fail (allow connection) when OCSP validation fails.
     */
    private boolean softFail;// = true;
    
    /**
     * Default cache duration in minutes for responses without nextUpdate.
     */
    private long defaultCacheDurationMinutes;// = 60;
    
    /**
     * Timeout in seconds for OCSP responder connections.
     */
    private int timeoutSeconds;// = 10;
}
