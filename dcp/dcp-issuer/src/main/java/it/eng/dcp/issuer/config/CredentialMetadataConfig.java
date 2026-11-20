package it.eng.dcp.issuer.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for supported credentials metadata.
 * Loaded from credential-metadata-configuration.properties file on classpath.
 */
@Getter
@Setter
public class CredentialMetadataConfig {

    /**
     * List of supported credential configurations.
     */
    private List<CredentialConfig> supported = new ArrayList<>();

    /**
     * Finds the supported credential with the given ID (the {@code credentialsSupported} entry a request
     * refers to, DCP §6.4.1).
     *
     * @param id the credential ID from a credential request
     * @return the configured credential, or empty if the ID is not supported
     */
    public Optional<CredentialConfig> findById(String id) {
        if (id == null || supported == null) {
            return Optional.empty();
        }
        return supported.stream().filter(config -> id.equals(config.getId())).findFirst();
    }

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
         * Reason for offering this credential (e.g., "issuance", "reissue"); required by the IssuerMetadata schema.
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

