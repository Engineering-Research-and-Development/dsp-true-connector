package it.eng.dataplane.httppull.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuration for the HTTP-PULL Data Plane.
 * The OkHttpClient bean used by ControlPlaneClient and ControlPlaneRegistrationBean is
 * provided automatically by OkHttpClientConfiguration in tools (component-scanned via
 * the application class). It reads server.ssl.enabled: true = TLS client with custom
 * truststore; false = insecure noop client for development.
 */
@Configuration
public class HttpPullConfiguration {

    /**
     * Virtual-thread executor for async HTTP-PULL transfers (Java 21).
     * Each transfer runs on its own virtual thread — no fixed pool ceiling,
     * blocked I/O does not park OS threads, so thousands of concurrent transfers
     * are practical without tuning thread pool sizes.
     *
     * @return virtual-thread-per-task executor
     */
    @Bean("transferExecutor")
    public Executor transferExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
