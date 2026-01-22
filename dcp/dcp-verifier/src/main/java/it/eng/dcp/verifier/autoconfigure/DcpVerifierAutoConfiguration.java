package it.eng.dcp.verifier.autoconfigure;

import it.eng.dcp.common.config.DcpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the DCP Verifier module.
 *
 * This configuration is registered using the Spring Boot auto-configuration discovery
 * mechanism. It component-scans the `it.eng.dcp.common` package to include
 * shared services from dcp-common module, particularly the SelfIssuedIdTokenService
 * which provides token validation capabilities.
 *
 * Uses DcpProperties with connectorDidVerifier field for verifier-specific DID.
 * It is conditional on the property `dcp.verifier.enabled` (defaults to enabled).
 */
@Configuration
@ConditionalOnProperty(prefix = "dcp.verifier", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DcpProperties.class)
@ComponentScan({
    "it.eng.dcp.common",
    "it.eng.dcp.verifier"
})
public class DcpVerifierAutoConfiguration {

}
