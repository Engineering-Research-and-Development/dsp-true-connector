package it.eng.dcp.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the DCP module.
 * Binds properties under the `dcp` prefix (e.g. `dcp.connector.did`).
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "dcp")
public class DcpProperties {

    /** The connector DID (e.g. did:web:example.com:connector). */
    @NotNull
    private String connectorDid;

    /** Base URL used by the connector when constructing endpoints. */
    private String baseUrl;

    /** Host name or IP address. */
    private String host = "localhost";

    /** Allowed clock skew in seconds for token validation. Defaults to 120 seconds. */
    @Min(0)
    private int clockSkewSeconds = 120;

    /** Supported profiles, e.g. ["VC11_SL2021_JWT","VC11_SL2021_JSONLD"] */
    private List<String> supportedProfiles = Collections.emptyList();

    /** Trusted issuers mapping per credential type. */
    private Map<String, List<String>> trustedIssuers = Collections.emptyMap();

    /** Keystore configuration. */
    private Keystore keystore = new Keystore();

    /** Issuer configuration. */
    private Issuer issuer = new Issuer();

    /** Enable Verifiable Presentation JWT for connector authentication. Defaults to false. */
    private Vp vp = new Vp();

    /**
     * Keystore configuration.
     */
    public static class Keystore {
        private String path = "eckey.p12";
        private String password = "password";
        private String alias = "dsptrueconnector";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }
    }

    /**
     * Issuer configuration.
     */
    public static class Issuer {
        /** Issuer location URL. */
        private String location;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
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
