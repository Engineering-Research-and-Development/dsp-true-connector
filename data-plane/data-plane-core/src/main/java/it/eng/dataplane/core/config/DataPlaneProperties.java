package it.eng.dataplane.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Configuration properties for a Data Plane instance.
 * Prefix: {@code dataplane}
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "dataplane")
public class DataPlaneProperties {

    /**
     * Unique identifier for this Data Plane instance.
     * Configure via {@code dataplane.id} for stable identification across restarts — required for
     * predictable graceful deregistration from the Control Plane on shutdown.
     * Defaults to a random UUID if not explicitly configured (changes every restart).
     */
    private String id = UUID.randomUUID().toString();

    /** This DP's public endpoint URL (used for self-registration with CP). */
    private String endpoint;

    /**
     * Control Plane base endpoint URL.
     * Populated at startup from {@code dataplane.control-plane-endpoint} property
     * or updated dynamically via PUT /controlplanes from the Control Plane.
     * Used by DataFlowService callbacks and routing in later phases.
     * Declared {@code volatile} to ensure visibility across threads when updated concurrently.
     */
    private volatile String controlPlaneEndpoint;

    /** Control Plane admin API endpoint URL (for registration calls). */
    private String controlPlaneAdminEndpoint;

    /** Auth type for CP-DP calls: API_KEY or OAUTH2. */
    private String authType = "API_KEY";

    /** API key value (when authType=API_KEY). */
    private String apiKey;

    /**
     * Shared bootstrap key this Data Plane presents as {@code X-Registration-Key} when first
     * enrolling with the Control Plane. Must match {@code dataplane.registration.bootstrap-key}
     * configured on the Control Plane. Leave blank to skip authentication (only valid when the
     * CP's registration endpoint has no bootstrap key configured — not recommended).
     */
    private String controlPlaneRegistrationKey;
}
