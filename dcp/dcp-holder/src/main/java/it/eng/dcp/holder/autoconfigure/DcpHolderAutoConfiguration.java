package it.eng.dcp.holder.autoconfigure;

import it.eng.dcp.common.config.DcpProperties;
import it.eng.dcp.holder.config.DCPMongoConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for the DCP Holder module.
 *
 * This configuration is registered using the Spring Boot auto-configuration discovery
 * mechanism. It enables the DCP properties binding, imports the Mongo configuration
 * and component-scans both `it.eng.dcp` and `it.eng.dcp.common` packages to include
 * shared services from dcp-common module.
 *
 * It is conditional on the property `dcp.enabled` (defaults to enabled).
 */
@Configuration
@ConditionalOnProperty(prefix = "dcp", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DcpProperties.class)
@Import({DCPMongoConfig.class})
@ComponentScan({
    "it.eng.dcp",
    "it.eng.dcp.common"
})
public class DcpHolderAutoConfiguration {

}

