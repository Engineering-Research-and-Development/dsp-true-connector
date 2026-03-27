package it.eng.datatransfer.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Spring configuration for data-transfer infrastructure beans.
 */
@Configuration
public class DataTransferConfiguration {

    /**
     * Bounded executor used by {@link it.eng.datatransfer.service.api.strategy.HttpPushTransferStrategy}
     * to run HTTP-PUSH transfers concurrently.
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
    @Bean(name = "httpPushTransferExecutor")
    public Executor httpPushTransferExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("http-push-transfer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}

