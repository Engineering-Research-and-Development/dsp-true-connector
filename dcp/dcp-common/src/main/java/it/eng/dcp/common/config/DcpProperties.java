package it.eng.dcp.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import it.eng.dcp.common.participant.ParticipantContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Development switch: allow {@code http} for DID resolution, discovered endpoints and advertised
     * endpoints. Defaults to false, so DCP endpoints must use HTTPS (DCP v1.0.1, Transport Security).
     */
    private boolean allowInsecureHttp = false;

    /** Allowed clock skew in seconds for token validation. Defaults to 120 seconds. */
    @Min(0)
    private int clockSkewSeconds = 120;

    /** Keystore configuration. */
    private Keystore keystore = new Keystore();

    /**
     * Map of credential types to comma-separated list of trusted issuer DIDs.
     * Example: dcp.trusted-issuers.MembershipCredential=did:web:localhost:8080,did:web:localhost:8090
     */
    private Map<String, String> trustedIssuers = new HashMap<>();

    /** Issuer configuration. */
    private Issuer issuer = new Issuer();

    /** Enable Verifiable Presentation JWT for connector authentication. Defaults to false. */
    private Vp vp = new Vp();

    /** Service entries to include in the DID document. */
    private List<ServiceEntry> serviceEntries = new ArrayList<>();

    /** Participant context resolution. */
    private Participant participant = new Participant();

    /** Base paths of the DCP controllers. */
    private BasePath basePath = new BasePath();

    /**
     * How long the public key of a rotated signing key stays published in the DID document, so that
     * tokens signed just before the rotation still verify. Defaults to 24 hours.
     */
    private Duration keyGracePeriod = Duration.ofHours(24);

    /**
     * Returns the base paths of the DCP and the issuer controllers.
     *
     * @return the configured base paths
     */
    public Set<String> basePaths() {
        return Set.of(basePath.getDcp(), basePath.getIssuer());
    }

    /**
     * Strategy to determine the participant context of a request.
     */
    public enum ParticipantResolver {
        /** Every request addresses the single configured participant. */
        FIXED,
        /** The participant is the path segment before the DCP base path. */
        PATH
    }

    /**
     * Participant context configuration.
     */
    @Setter
    @Getter
    public static class Participant {
        /** Resolver strategy: {@code fixed} (default) or {@code path}. */
        private ParticipantResolver resolver = ParticipantResolver.FIXED;

        /** ID of the single participant built from these properties. */
        private String id = ParticipantContext.DEFAULT_ID;

        /**
         * IDs that no participant may use, because they collide with zone prefixes. The configured DCP
         * base paths are always reserved in addition.
         */
        private Set<String> reservedIds = new LinkedHashSet<>(List.of(
                "internal", "api", "actuator", ".well-known", "connector", "catalog", "negotiations", "transfers"));
    }

    /**
     * Base paths of the DCP controllers.
     */
    @Setter
    @Getter
    public static class BasePath {
        /** Base path of the holder / credential service endpoints. */
        private String dcp = "dcp";

        /** Base path of the issuer endpoints. */
        private String issuer = "issuer";
    }

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
