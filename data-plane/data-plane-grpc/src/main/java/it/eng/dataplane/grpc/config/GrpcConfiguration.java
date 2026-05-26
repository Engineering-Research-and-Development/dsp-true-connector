package it.eng.dataplane.grpc.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the gRPC Data Plane module.
 *
 * <p>Enables {@link GrpcProperties} binding. The actual gRPC server bean will be wired
 * here in a subsequent task when provider-side streaming is implemented.</p>
 */
@Configuration
@EnableConfigurationProperties(GrpcProperties.class)
public class GrpcConfiguration {
}
