package it.eng.dcp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration for supported credentials metadata.
 * Binds properties under the `dcp.credentials` prefix.
 */
@Component
@ConfigurationProperties(prefix = "dcp.credentials")
@Getter
@Setter
public class CredentialMetadataConfig {

    /**
     * List of supported credential configurations.
     */
    private List<CredentialConfig> supported = new ArrayList<>();

    @Getter
    @Setter
    public static class CredentialConfig {
        /**
         * Unique identifier for this credential.
         */
        private String id;

        /**
         * Type of the credential object (e.g., "CredentialObject").
         */
        private String type = "CredentialObject";

        /**
         * The credential type (e.g., "MembershipCredential", "CompanyCredential").
         */
        private String credentialType;

        /**
         * Optional reason for offering this credential (e.g., "reissue", "new").
         */
        private String offerReason;

        /**
         * URL to the credential schema.
         */
        private String credentialSchema;

        /**
         * List of supported binding methods (e.g., ["did:web", "did:key"]).
         */
        private List<String> bindingMethods = new ArrayList<>();

        /**
         * Profile identifier (e.g., "vc10-sl2021/jwt", "vc11-sl2021/jsonld").
         */
        private String profile;

        /**
         * Issuance policy as a map structure.
         */
        private Map<String, Object> issuancePolicy;
    }
}

