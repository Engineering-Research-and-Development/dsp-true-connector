package it.eng.dcp.issuer.service;

import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.issuer.config.CredentialMetadataConfig;
import it.eng.dcp.issuer.config.IssuerProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/**
 * Service for managing credential metadata and generating IssuerMetadata.
 */
@Service
@Slf4j
public class CredentialMetadataService {

    private static final String CONFIG_PREFIX = "dcp.credentials.supported";

    private final IssuerProperties issuerProperties;
    private final CredentialMetadataConfig credentialMetadataConfig;

    @Autowired
    public CredentialMetadataService(IssuerProperties issuerProperties,
                                    CredentialMetadataConfig credentialMetadataConfig) {
        this.issuerProperties = issuerProperties;
        this.credentialMetadataConfig = credentialMetadataConfig;
    }

    /**
     * Checks at startup that every configured credential yields a {@code credentialsSupported} entry that
     * holders can request and that the IssuerMetadata schema accepts: a unique, stable {@code id}, and the
     * five required properties {@code credentialType}, {@code credentialSchema}, {@code offerReason},
     * {@code bindingMethods} and {@code profile}. {@code bindingMethods} and {@code profile} have defaults;
     * the others must be configured.
     *
     * @throws IllegalStateException if an entry misses a required property or two entries share an id
     */
    @PostConstruct
    public void validateSupportedCredentials() {
        List<String> problems = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        List<CredentialMetadataConfig.CredentialConfig> supported = credentialMetadataConfig.getSupported();
        for (int i = 0; i < supported.size(); i++) {
            CredentialMetadataConfig.CredentialConfig config = supported.get(i);
            String name = CONFIG_PREFIX + "[" + i + "]"
                    + (isBlank(config.getCredentialType()) ? "" : " (" + config.getCredentialType() + ")");
            List<String> missing = new ArrayList<>();
            if (isBlank(config.getId())) {
                missing.add("id");
            } else if (!ids.add(config.getId())) {
                problems.add(name + " repeats id " + config.getId());
            }
            if (isBlank(config.getCredentialType())) {
                missing.add("credentialType");
            }
            if (isBlank(config.getCredentialSchema())) {
                missing.add("credentialSchema");
            }
            if (isBlank(config.getOfferReason())) {
                missing.add("offerReason");
            }
            if (!missing.isEmpty()) {
                problems.add(name + " is missing " + String.join(", ", missing));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid supported credential configuration: " + String.join("; ", problems));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Build IssuerMetadata based on configuration.
     *
     * @return IssuerMetadata with configured credentials
     */
    public IssuerMetadata buildIssuerMetadata() {
        String issuerDid = issuerProperties.getConnectorDid();
        if (issuerDid == null || issuerDid.isBlank()) {
            issuerDid = "did:web:issuer-url";
            log.warn("No issuer DID configured, using default: {}", issuerDid);
        }

        log.info("Building metadata for {} configured credentials", credentialMetadataConfig.getSupported().size());
        List<IssuerMetadata.CredentialObject> credentialObjects = new ArrayList<>();

        for (CredentialMetadataConfig.CredentialConfig config : credentialMetadataConfig.getSupported()) {
            try {
                IssuerMetadata.CredentialObject credentialObject = buildCredentialObject(config);
                credentialObjects.add(credentialObject);
            } catch (Exception e) {
                log.error("Failed to build credential object for type '{}': {}",
                        config.getCredentialType(), e.getMessage(), e);
            }
        }

        // credentialsSupported is required by the IssuerMetadata schema; an empty array is valid
        if (credentialObjects.isEmpty() && !credentialMetadataConfig.getSupported().isEmpty()) {
            log.error("All {} configured credentials failed to build; issuer metadata lists none",
                    credentialMetadataConfig.getSupported().size());
        } else if (credentialObjects.isEmpty()) {
            log.warn("Issuer metadata has no supported credentials");
        }

        return IssuerMetadata.Builder.newInstance()
            .issuer(issuerDid)
            .credentialsSupported(credentialObjects)
            .build();
    }

    /**
     * Build a CredentialObject from configuration.
     *
     * @param config The credential configuration
     * @return A CredentialObject
     */
    private IssuerMetadata.CredentialObject buildCredentialObject(CredentialMetadataConfig.CredentialConfig config) {
        IssuerMetadata.CredentialObject.Builder builder = IssuerMetadata.CredentialObject.Builder.newInstance();

        String id = config.getId();
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            log.debug("Generated ID for credential type '{}': {}", config.getCredentialType(), id);
        }
        builder.id(id);

        if (config.getCredentialType() == null || config.getCredentialType().isBlank()) {
            throw new IllegalArgumentException("credentialType is required");
        }
        builder.credentialType(config.getCredentialType());

        if (config.getCredentialSchema() != null && !config.getCredentialSchema().isBlank()) {
            builder.credentialSchema(config.getCredentialSchema());
        }

        if (config.getOfferReason() != null && !config.getOfferReason().isBlank()) {
            builder.offerReason(config.getOfferReason());
        }

        if (config.getBindingMethods() != null && !config.getBindingMethods().isEmpty()) {
            builder.bindingMethods(config.getBindingMethods());
        } else {
            builder.bindingMethods(List.of("did:web"));
        }

        if (config.getProfile() != null && !config.getProfile().isBlank()) {
            builder.profile(config.getProfile());
        } else {
            String defaultProfile = determineDefaultProfile();
            builder.profile(defaultProfile);
        }

        if (config.getIssuancePolicy() != null) {
            builder.issuancePolicy(config.getIssuancePolicy());
        }

        return builder.build();
    }

    /**
     * Determine the default profile to use from configuration.
     * Returns the first configured profile, or defaults to VC 2.0 profile.
     *
     * @return Default profile string (spec-compliant alias)
     */
    private String determineDefaultProfile() {
        List<String> supportedProfiles = issuerProperties.getSupportedProfiles();
        if (supportedProfiles != null && !supportedProfiles.isEmpty()) {
            String profile = supportedProfiles.get(0);
            log.debug("Using first supported profile as default: {}", profile);
            return profile;  // Already in spec format (e.g., "vc20-bssl/jwt")
        }
        // Default to VC 2.0 profile if none configured (recommended by DCP specification)
        return ProfileId.VC20_BSSL_JWT.toString();
    }
}

