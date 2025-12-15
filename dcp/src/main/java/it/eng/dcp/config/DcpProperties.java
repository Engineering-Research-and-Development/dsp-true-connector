package it.eng.dcp.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the DCP module.
 * Binds properties under the `dcp` prefix (e.g. `dcp.connector.did`).
 */
@ConfigurationProperties(prefix = "dcp")
public class DcpProperties {

    /** The connector DID (e.g. did:web:example.com:connector). */
    @NotNull
    private String connectorDid;

    /** Base URL used by the connector when constructing endpoints. */
    private String baseUrl;

    /** Allowed clock skew in seconds for token validation. Defaults to 120 seconds. */
    @Min(0)
    private int clockSkewSeconds = 120;

    /** Supported profiles, e.g. ["VC11_SL2021_JWT","VC11_SL2021_JSONLD"] */
    private List<String> supportedProfiles = Collections.emptyList();

    /** Trusted issuers mapping per credential type. */
    private Map<String, List<String>> trustedIssuers = Collections.emptyMap();

    /** Enable Verifiable Presentation JWT for connector authentication. Defaults to false. */
    private Vp vp = new Vp();

    public String getConnectorDid() {
        return connectorDid;
    }

    public void setConnectorDid(String connectorDid) {
        this.connectorDid = connectorDid;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(int clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public List<String> getSupportedProfiles() {
        return supportedProfiles;
    }

    public void setSupportedProfiles(List<String> supportedProfiles) {
        this.supportedProfiles = supportedProfiles;
    }

    public Map<String, List<String>> getTrustedIssuers() {
        return trustedIssuers;
    }

    public void setTrustedIssuers(Map<String, List<String>> trustedIssuers) {
        this.trustedIssuers = trustedIssuers;
    }

    public Vp getVp() {
        return vp;
    }

    public void setVp(Vp vp) {
        this.vp = vp;
    }

    /**
     * Configuration for Verifiable Presentation JWT authentication.
     */
    public static class Vp {
        /** Enable VP JWT for connector authentication. */
        private boolean enabled = false;

        /** Credential types to include in VP (comma-separated). Empty means all credentials. */
        private String scope = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }
    }
}
