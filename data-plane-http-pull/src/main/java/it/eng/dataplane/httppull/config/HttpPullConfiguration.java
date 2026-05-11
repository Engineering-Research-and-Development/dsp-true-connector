package it.eng.dataplane.httppull.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

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
     * Bounded executor used by {@link it.eng.dataplane.httppull.HttpPullTransferProtocol}
     * to run HTTP-PULL transfers concurrently.
     *
     * <p>{@link ThreadPoolTaskExecutor} is a Spring-managed bean: its thread pool is
     * initialised on application start and shut down gracefully (via {@code ExecutorService.shutdown()})
     * when the Spring context closes, preventing thread leaks across application and test lifecycles.
     *
     * <p>Pool sizing rationale: each concurrent transfer may hold up to ~50 MB of heap for buffered
     * data; 8 concurrent transfers correspond to approximately 400 MB. Tune
     * {@code maxPoolSize} and the queue capacity to match available RAM and expected workload.
     *
     * @return a configured {@link ThreadPoolTaskExecutor} with a core/max pool size of 8
     */
    @Bean("transferExecutor")
    public Executor transferExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("http-pull-transfer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
