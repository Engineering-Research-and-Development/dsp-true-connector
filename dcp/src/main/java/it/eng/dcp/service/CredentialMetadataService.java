package it.eng.dcp.service;

import it.eng.dcp.config.CredentialMetadataConfig;
import it.eng.dcp.config.DcpProperties;
import it.eng.dcp.model.IssuerMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing credential metadata and generating IssuerMetadata.
 * This service provides configuration-based credential metadata.
 * Requires credentials to be configured in dcp.credentials.supported properties.
 */
@Service
@Slf4j
public class CredentialMetadataService {

    private final DcpProperties dcpProperties;
    private final CredentialMetadataConfig credentialMetadataConfig;

    @Autowired
    public CredentialMetadataService(DcpProperties dcpProperties,
                                    CredentialMetadataConfig credentialMetadataConfig) {
        this.dcpProperties = dcpProperties;
        this.credentialMetadataConfig = credentialMetadataConfig;
    }

    /**
     * Build IssuerMetadata based on configuration.
     * Throws IllegalStateException if no credentials are configured.
     *
     * @return IssuerMetadata with configured credentials
     * @throws IllegalStateException if no credentials are configured in dcp.credentials.supported
     */
    public IssuerMetadata buildIssuerMetadata() {
        String issuerDid = dcpProperties.getConnectorDid();
        if (issuerDid == null || issuerDid.isBlank()) {
            issuerDid = "did:web:issuer-url";
            log.warn("No connector DID configured, using default: {}", issuerDid);
        }

        if (credentialMetadataConfig.getSupported().isEmpty()) {
            log.error("No credentials configured in dcp.credentials.supported - cannot build issuer metadata");
            throw new IllegalStateException(
                "No credentials configured. Please configure at least one credential in dcp.credentials.supported properties");
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

        // ID: use configured or generate UUID
        String id = config.getId();
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            log.debug("Generated ID for credential type '{}': {}", config.getCredentialType(), id);
        }
        builder.id(id);

        // Type: use configured or default
        String type = config.getType();
        if (type == null || type.isBlank()) {
            type = "CredentialObject";
        }
        builder.type(type);

        // CredentialType: required
        if (config.getCredentialType() == null || config.getCredentialType().isBlank()) {
            throw new IllegalArgumentException("credentialType is required");
        }
        builder.credentialType(config.getCredentialType());

        // Optional fields
        if (config.getCredentialSchema() != null && !config.getCredentialSchema().isBlank()) {
            builder.credentialSchema(config.getCredentialSchema());
        }

        if (config.getBindingMethods() != null && !config.getBindingMethods().isEmpty()) {
            builder.bindingMethods(config.getBindingMethods());
        } else {
            // Default binding methods
            builder.bindingMethods(List.of("did:web"));
        }

        if (config.getProfile() != null && !config.getProfile().isBlank()) {
            builder.profile(config.getProfile());
        } else {
            // Default profile based on supported profiles in DcpProperties
            String defaultProfile = determineDefaultProfile();
            builder.profile(defaultProfile);
        }

        if (config.getIssuancePolicy() != null) {
            builder.issuancePolicy(config.getIssuancePolicy());
        }

        return builder.build();
    }

    /**
     * Determine the default profile to use.
     * Uses the first supported profile from configuration, or a fallback.
     *
     * @return Default profile string
     */
    private String determineDefaultProfile() {
        List<String> supportedProfiles = dcpProperties.getSupportedProfiles();
        if (supportedProfiles != null && !supportedProfiles.isEmpty()) {
            String profile = supportedProfiles.get(0);
            log.debug("Using first supported profile as default: {}", profile);
            return normalizeProfileId(profile);
        }

        // Fallback to JWT profile
        return "vc11-sl2021/jwt";
    }

    /**
     * Normalize profile ID to lowercase with slashes.
     * Converts "VC11_SL2021_JWT" to "vc11-sl2021/jwt".
     *
     * @param profileId The profile ID to normalize
     * @return Normalized profile string
     */
    private String normalizeProfileId(String profileId) {
        if (profileId == null) {
            return "vc11-sl2021/jwt";
        }

        // Convert VC11_SL2021_JWT to vc11-sl2021/jwt
        return profileId.toLowerCase()
            .replace("_sl", "-sl")
            .replace("_", "/");
    }
}

