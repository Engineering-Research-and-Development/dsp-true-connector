package it.eng.dataplane.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for a Data Plane instance.
 * Prefix: {@code dataplane}
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "dataplane")
public class DataPlaneProperties {

    /** This DP's public endpoint URL (used for self-registration with CP). */
    private String endpoint;

    /**
     * Control Plane base endpoint URL.
     * Populated at startup from {@code dataplane.control-plane-endpoint} property
     * or updated dynamically via PUT /controlplanes from the Control Plane.
     * Used by DataFlowService callbacks and routing in later phases.
     */
    private String controlPlaneEndpoint;

    /** Control Plane admin API endpoint URL (for registration calls). */
    private String controlPlaneAdminEndpoint;

    /** Auth type for CP-DP calls: API_KEY or OAUTH2. */
    private String authType = "API_KEY";

    /** API key value (when authType=API_KEY). */
    private String apiKey;
}
