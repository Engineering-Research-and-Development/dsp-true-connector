package it.eng.dcp.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the DCP module.
 * Binds properties under the `dcp` prefix (e.g. `dcp.connector.did`).
 * Used by both holder and verifier modules.
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "dcp")
public class DcpProperties {

    /** The connector DID (e.g. did:web:example.com:connector). */
    @NotNull
    private String connectorDid;

    /** The connector DID for verifier role (e.g., did:web:example.com:connector:verifier).
     * If not set, falls back to connectorDid. */
    private String connectorDidVerifier;

    /**
     * Enable automatic path-based endpoint registration.
     * If true, controller will register endpoints based on DID path segments.
     * If false, only /.well-known/did.json will be registered.
     * Default: true
     */
    private boolean autoRegisterPathEndpoints = true;

    /**
     * Enable legacy convenience endpoints (e.g., /issuer/did.json).
     * If false, only W3C-compliant endpoints will be registered.
     * Default: true (for backward compatibility)
     */
    private boolean enableLegacyEndpoints = true;

    /** Base URL used by the connector when constructing endpoints. */
    private String baseUrl;

    /** Host name or IP address. */
    private String host = "localhost";

    /** Allowed clock skew in seconds for token validation. Defaults to 120 seconds. */
    @Min(0)
    private int clockSkewSeconds = 120;

    /** Keystore configuration. */
    private Keystore keystore = new Keystore();

    /** Trusted issuers configuration. */
    private TrustedIssuers trustedIssuers = new TrustedIssuers();

    /** Issuer configuration. */
    private Issuer issuer = new Issuer();

    /** Enable Verifiable Presentation JWT for connector authentication. Defaults to false. */
    private Vp vp = new Vp();

    /** Service entries to include in the DID document. */
    private List<ServiceEntry> serviceEntries = new ArrayList<>();

    /**
     * Keystore configuration.
     */
    @Setter
    @Getter
    public static class Keystore {
        private String path = "eckey.p12";
        private String password = "password";
        private String alias = "dsptrueconnector";

    }

    /**
     * Issuer configuration.
     */
    @Setter
    @Getter
    public static class Issuer {
        /** Issuer location URL. */
        private String location;

    }

    /**
     * Configuration for Verifiable Presentation JWT authentication.
     */
    @Setter
    @Getter
    public static class Vp {
        /** Enable VP JWT for connector authentication. */
        private boolean enabled = false;

        /** Credential types to include in VP (comma-separated). Empty means all credentials. */
        private String scope = "";

    }

    /**
     * Configuration for trusted issuers per credential type.
     * Maps credential types to comma-separated lists of trusted issuer DIDs.
     */
    @Setter
    @Getter
    public static class TrustedIssuers {
        /**
         * Map of credential types to comma-separated list of trusted issuer DIDs.
         * Example: MembershipCredential=did:web:localhost:8080,did:web:localhost:8090
         */
        private Map<String, String> issuers = Collections.emptyMap();

    }

    /**
     * Configuration for a service entry in the DID document.
     */
    @Setter
    @Getter
    public static class ServiceEntry {
        /**
         * Service ID (e.g., "TRUEConnector-Credential-Service").
         */
        private String id;

        /**
         * Service type (e.g., "CredentialService", "IssuerService", "VerifierService").
         */
        private String type;

        /**
         * Service endpoint path (will be appended to base URL).
         * Leave empty to use issuerLocation instead.
         */
        private String endpointPath = "";

        /**
         * Issuer location URL (optional, used instead of endpoint path for issuer services).
         */
        private String issuerLocation = "";

    }
}
