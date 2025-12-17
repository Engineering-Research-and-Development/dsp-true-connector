package it.eng.dcp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Configuration loader for credential metadata from classpath properties file.
 * Loads configuration from credential-metadata-configuration.properties file.
 */
@Configuration
@Slf4j
public class CredentialMetadataConfigLoader {

    private static final String CONFIG_FILE = "credential-metadata-configuration.properties";
    private static final String CREDENTIALS_PREFIX = "dcp.credentials.supported";
    private static final String PROFILES_PREFIX = "dcp.supportedProfiles";
    private static final String CONNECTOR_DID_KEY = "dcp.connector-did";

    @Bean
    public CredentialMetadataConfig credentialMetadataConfig() {
        log.info("Loading credential metadata configuration from classpath: {}", CONFIG_FILE);

        Properties properties = loadPropertiesFromClasspath();
        CredentialMetadataConfig config = new CredentialMetadataConfig();

        // Parse credentials
        List<CredentialMetadataConfig.CredentialConfig> credentials = parseCredentials(properties);
        config.setSupported(credentials);

        log.info("Loaded {} credential configurations from {}", credentials.size(), CONFIG_FILE);

        return config;
    }

    /**
     * Load properties from classpath.
     *
     * @return Properties loaded from the configuration file
     */
    private Properties loadPropertiesFromClasspath() {
        Properties properties = new Properties();

        try {
            ClassPathResource resource = new ClassPathResource(CONFIG_FILE);
            if (!resource.exists()) {
                log.warn("Credential metadata configuration file not found on classpath: {}", CONFIG_FILE);
                return properties;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                properties.load(inputStream);
                log.debug("Successfully loaded {} properties from {}", properties.size(), CONFIG_FILE);
            }
        } catch (IOException e) {
            log.error("Failed to load credential metadata configuration from {}: {}",
                    CONFIG_FILE, e.getMessage(), e);
        }

        return properties;
    }

    /**
     * Parse credential configurations from properties.
     * Format: dcp.credentials.supported[index].fieldName=value
     *
     * @param properties Properties to parse
     * @return List of parsed credential configurations
     */
    private List<CredentialMetadataConfig.CredentialConfig> parseCredentials(Properties properties) {
        Map<Integer, CredentialMetadataConfig.CredentialConfig> credentialMap = new TreeMap<>();

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(CREDENTIALS_PREFIX)) {
                continue;
            }

            try {
                // Extract index and field name from key like: dcp.credentials.supported[0].credentialType
                String afterPrefix = key.substring(CREDENTIALS_PREFIX.length());

                if (!afterPrefix.startsWith("[")) {
                    continue;
                }

                int endBracket = afterPrefix.indexOf("]");
                if (endBracket == -1) {
                    continue;
                }

                int index = Integer.parseInt(afterPrefix.substring(1, endBracket));
                String fieldPath = afterPrefix.substring(endBracket + 2); // Skip "]."

                // Get or create credential config for this index
                CredentialMetadataConfig.CredentialConfig config =
                        credentialMap.computeIfAbsent(index, k -> new CredentialMetadataConfig.CredentialConfig());

                // Set the field value
                setFieldValue(config, fieldPath, properties.getProperty(key));

            } catch (Exception e) {
                log.warn("Failed to parse property {}: {}", key, e.getMessage());
            }
        }

        return new ArrayList<>(credentialMap.values());
    }

    /**
     * Set field value on credential config.
     * Handles nested properties like bindingMethods[0], issuancePolicy.id, etc.
     *
     * @param config Credential configuration to update
     * @param fieldPath Path to the field (e.g., "credentialType", "bindingMethods[0]")
     * @param value Value to set
     */
    private void setFieldValue(CredentialMetadataConfig.CredentialConfig config,
                               String fieldPath, String value) {

        if (fieldPath.startsWith("bindingMethods[")) {
            // Handle array: bindingMethods[0]=did:web
            if (config.getBindingMethods() == null) {
                config.setBindingMethods(new ArrayList<>());
            }
            config.getBindingMethods().add(value);

        } else if (fieldPath.startsWith("issuancePolicy.")) {
            // Handle nested map: issuancePolicy.id=value
            if (config.getIssuancePolicy() == null) {
                config.setIssuancePolicy(new HashMap<>());
            }
            String policyPath = fieldPath.substring("issuancePolicy.".length());
            setNestedMapValue(config.getIssuancePolicy(), policyPath, value);

        } else {
            // Handle simple fields
            switch (fieldPath) {
                case "id":
                    config.setId(value);
                    break;
                case "type":
                    config.setType(value);
                    break;
                case "credentialType":
                    config.setCredentialType(value);
                    break;
                case "offerReason":
                    config.setOfferReason(value);
                    break;
                case "credentialSchema":
                    config.setCredentialSchema(value);
                    break;
                case "profile":
                    config.setProfile(value);
                    break;
                default:
                    log.debug("Unknown field path: {}", fieldPath);
            }
        }
    }

    /**
     * Set nested map value for complex paths.
     * Handles paths like input_descriptors[0].id=value or
     * input_descriptors[0].constraints.fields[0].path[0]=value.
     *
     * @param map Map to update
     * @param path Path within the map
     * @param value Value to set
     */
    @SuppressWarnings("unchecked")
    private void setNestedMapValue(Map<String, Object> map, String path, String value) {
        String[] parts = path.split("\\.", 2);
        String currentKey = parts[0];

        // Handle array notation: input_descriptors[0]
        if (currentKey.contains("[")) {
            int bracketStart = currentKey.indexOf("[");
            int bracketEnd = currentKey.indexOf("]");
            String arrayKey = currentKey.substring(0, bracketStart);
            int index = Integer.parseInt(currentKey.substring(bracketStart + 1, bracketEnd));

            // Get or create list
            List<Object> list = (List<Object>) map.computeIfAbsent(arrayKey, k -> new ArrayList<>());

            // Ensure list has enough elements
            while (list.size() <= index) {
                list.add(new HashMap<String, Object>());
            }

            if (parts.length == 1) {
                // Leaf value
                list.set(index, value);
            } else {
                // Nested structure
                Map<String, Object> nestedMap = (Map<String, Object>) list.get(index);
                if (nestedMap == null) {
                    nestedMap = new HashMap<>();
                    list.set(index, nestedMap);
                }
                setNestedMapValue(nestedMap, parts[1], value);
            }
        } else {
            // Simple key
            if (parts.length == 1) {
                // Leaf value
                map.put(currentKey, value);
            } else {
                // Nested structure
                Map<String, Object> nestedMap = (Map<String, Object>)
                        map.computeIfAbsent(currentKey, k -> new HashMap<>());
                setNestedMapValue(nestedMap, parts[1], value);
            }
        }
    }
}

