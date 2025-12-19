package it.eng.dcp.common.config;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Configuration for DID document generation.
 * This allows different modules (holder, issuer) to customize their DID documents.
 */
@Getter
@Builder
public class DidDocumentConfig {

    /**
     * The DID identifier (e.g., "did:web:example.com:holder").
     */
    private final String did;

    /**
     * Base URL for service endpoints (e.g., "https://example.com:8080").
     * If not provided, will be constructed from protocol, host, and port.
     */
    private final String baseUrl;

    /**
     * Protocol to use (http or https).
     */
    @Builder.Default
    private final String protocol = "https";

    /**
     * Host name or IP address.
     */
    @Builder.Default
    private final String host = "localhost";

    /**
     * Port number.
     */
    private final String port;

    /**
     * Service entries to include in the DID document.
     */
    private final List<ServiceEntryConfig> serviceEntries;

    /**
     * Controller DID for verification methods.
     * If not provided, will use the main DID.
     */
    private final String verificationMethodController;

    /**
     * Keystore file path.
     */
    @Builder.Default
    private final String keystorePath = "eckey.p12";

    /**
     * Keystore password.
     */
    @Builder.Default
    private final String keystorePassword = "password";

    /**
     * Get the effective base URL.
     * If baseUrl is set, use it; otherwise construct from protocol, host, and port.
     *
     * @return the base URL
     */
    public String getEffectiveBaseUrl() {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl;
        }
        return protocol + "://" + host + ":" + port;
    }

    /**
     * Get the effective controller for verification methods.
     * If verificationMethodController is set, use it; otherwise use the main DID.
     *
     * @return the controller DID
     */
    public String getEffectiveController() {
        if (verificationMethodController != null && !verificationMethodController.isBlank()) {
            return verificationMethodController;
        }
        return did;
    }

    /**
     * Configuration for a service entry in the DID document.
     */
    @Getter
    @Builder
    public static class ServiceEntryConfig {
        /**
         * Service ID.
         */
        private final String id;

        /**
         * Service type.
         */
        private final String type;

        /**
         * Service endpoint path (will be appended to base URL).
         * Leave empty for base URL only.
         */
        @Builder.Default
        private final String endpointPath = "";

        /**
         * Get the full service endpoint.
         *
         * @param baseUrl the base URL
         * @return the full endpoint URL
         */
        public String getFullEndpoint(String baseUrl) {
            if (endpointPath == null || endpointPath.isBlank()) {
                return baseUrl;
            }
            return baseUrl + (endpointPath.startsWith("/") ? "" : "/") + endpointPath;
        }
    }
}

