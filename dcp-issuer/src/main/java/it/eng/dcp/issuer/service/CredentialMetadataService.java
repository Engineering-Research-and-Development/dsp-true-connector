package it.eng.dcp.issuer.service;

import it.eng.dcp.common.model.IssuerMetadata;
import it.eng.dcp.common.model.ProfileId;
import it.eng.dcp.issuer.config.CredentialMetadataConfig;
import it.eng.dcp.issuer.config.IssuerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing credential metadata and generating IssuerMetadata.
 */
@Service
@Slf4j
public class CredentialMetadataService {

    private final IssuerProperties issuerProperties;
    private final CredentialMetadataConfig credentialMetadataConfig;

    @Autowired
    public CredentialMetadataService(IssuerProperties issuerProperties,
                                    CredentialMetadataConfig credentialMetadataConfig) {
        this.issuerProperties = issuerProperties;
        this.credentialMetadataConfig = credentialMetadataConfig;
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

        if (credentialMetadataConfig.getSupported().isEmpty()) {
            log.error("No credentials configured in issuer.credentials.supported - cannot build issuer metadata");
            throw new IllegalStateException(
                "No credentials configured. Please configure at least one credential in issuer.credentials.supported properties");
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

        if (credentialObjects.isEmpty()) {
            log.error("All configured credentials failed to build - no valid credential objects available");
            throw new IllegalStateException(
                "Failed to build any valid credentials from configuration. Check logs for details.");
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

