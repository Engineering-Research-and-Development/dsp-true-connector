package it.eng.datatransfer.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;

/**
 * Spring configuration for data-transfer infrastructure beans.
 */
@Configuration
public class DataTransferConfiguration {

    /** Pool size for the task scheduler used by automatic transfer retry scheduling. */
    @Value("${application.transfer.scheduler.pool-size:5}")
    private int schedulerPoolSize = 5;

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
     * <p>A {@link it.eng.tools.configuration.TenantContextTaskDecorator} is installed so that
     * transfer worker threads inherit the tenant context from the submitting thread.
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
        executor.setTaskDecorator(new it.eng.tools.configuration.TenantContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Bounded executor used by {@link it.eng.datatransfer.service.api.strategy.HttpPullTransferStrategy}
     * to run HTTP-PULL transfers concurrently.
     *
     * <p>Uses the same pool sizing and lifecycle guarantees as {@link #httpPushTransferExecutor()}.
     * In-flight downloads complete before the Spring context closes thanks to
     * {@code waitForTasksToCompleteOnShutdown=true}, preventing partial writes to S3.
     *
     * <p>A {@link it.eng.tools.configuration.TenantContextTaskDecorator} is installed so that
     * transfer worker threads inherit the tenant context from the submitting thread.
     *
     * @return a configured {@link ThreadPoolTaskExecutor} with a core/max pool size of 8
     */
    @Bean(name = "httpPullTransferExecutor")
    public Executor httpPullTransferExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("http-pull-transfer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setTaskDecorator(new it.eng.tools.configuration.TenantContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * Creates the {@link TaskScheduler} used by {@link it.eng.datatransfer.service.AutomaticDataTransferService}
     * to schedule non-blocking retries after a failed protocol message attempt.
     *
     * <p>{@link ThreadPoolTaskScheduler} participates in the Spring lifecycle and shuts down
     * gracefully when the context closes, ensuring scheduled retries are not abandoned mid-flight.
     *
     * <p>A {@link it.eng.tools.configuration.TenantContextTaskDecorator} is installed so that
     * scheduled retry tasks inherit the tenant context from the submitting thread.
     *
     * @return configured {@link TaskScheduler}
     */
    @Bean(name = "transferTaskScheduler")
    public TaskScheduler transferTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(schedulerPoolSize);
        scheduler.setThreadNamePrefix("transfer-retry-");
        scheduler.setTaskDecorator(new it.eng.tools.configuration.TenantContextTaskDecorator());
        scheduler.initialize();
        return scheduler;
    }
}

