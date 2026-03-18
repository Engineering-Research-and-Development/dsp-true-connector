package it.eng.tools.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configures asynchronous Spring application event dispatching.
 *
 * <p>Uses a bounded {@link ThreadPoolTaskExecutor} instead of {@code SimpleAsyncTaskExecutor}
 * to prevent unbounded thread growth when event listeners block (e.g. during retry delays
 * in automatic negotiation).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsynchronousSpringEventsConfig {

    /** Core thread-pool size for async event dispatch. */
    @Value("${application.events.executor.core-pool-size:5}")
    private int corePoolSize = 5;

    /** Maximum thread-pool size for async event dispatch. */
    @Value("${application.events.executor.max-pool-size:20}")
    private int maxPoolSize = 20;

    /** Capacity of the task queue before new threads above core size are spawned. */
    @Value("${application.events.executor.queue-capacity:50}")
    private int queueCapacity = 50;

    /** Pool size for the task scheduler used by retry scheduling. */
    @Value("${application.events.scheduler.pool-size:5}")
    private int schedulerPoolSize = 5;

    /**
     * Creates the application-wide async event multicaster backed by a bounded thread pool.
     *
     * @return configured {@link ApplicationEventMulticaster}
     */
	@Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster simpleApplicationEventMulticaster() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("event-async-");
        executor.initialize();

        var eventMulticaster = new SimpleApplicationEventMulticaster();
        eventMulticaster.setTaskExecutor(executor);
        return eventMulticaster;
    }

    /**
     * Creates the application-wide {@link TaskScheduler} used by {@code AutomaticNegotiationService}
     * to schedule non-blocking retries after a failed protocol message attempt.
     *
     * @return configured {@link TaskScheduler}
     */
    @Bean
    public TaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(schedulerPoolSize);
        scheduler.setThreadNamePrefix("negotiation-retry-");
        scheduler.initialize();
        return scheduler;
    }
}
