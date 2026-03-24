package it.eng.tools.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configures asynchronous Spring application event dispatching.
 *
 * <p>Uses a bounded {@link ThreadPoolTaskExecutor} instead of {@code SimpleAsyncTaskExecutor}
 * to prevent unbounded thread growth when event listeners block (e.g. during retry delays
 * in automatic negotiation).
 *
 * <p>A custom {@link RejectedExecutionHandler} is installed so that when the bounded queue is
 * exhausted the rejected task runs in the caller's thread instead of throwing
 * {@link java.util.concurrent.RejectedExecutionException}. This guarantees that
 * {@code ApplicationEventMulticaster#publishEvent} never propagates an exception to HTTP request
 * handlers after a negotiation has already been persisted.
 */
@Slf4j
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
     * Default executor for {@code @Async} methods and async event dispatch.
     *
     * <p>Named {@code taskExecutor} following the Spring convention: when multiple
     * {@link TaskExecutor} beans are present, {@code AnnotationAsyncExecutionInterceptor}
     * looks for this name to avoid the "More than one TaskExecutor bean found" warning.
     *
     * @return configured {@link ThreadPoolTaskExecutor}
     */
    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("event-async-");
        executor.setRejectedExecutionHandler(new CallerRunsOrDiscardPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Creates the application-wide async event multicaster backed by a bounded thread pool.
     *
     * <p>A {@link CallerRunsOrDiscardPolicy} is installed as the rejection handler so that a
     * full queue never causes {@code publishEvent} to throw {@link java.util.concurrent.RejectedExecutionException}
     * into HTTP request handlers.
     *
     * @return configured {@link ApplicationEventMulticaster}
     */
    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster simpleApplicationEventMulticaster() {
        var eventMulticaster = new SimpleApplicationEventMulticaster();
        eventMulticaster.setTaskExecutor(taskExecutor());
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

    /**
     * Rejection policy for the event executor.
     *
     * <p>When the bounded queue is exhausted the task is executed synchronously on the caller's
     * thread so that no event is silently dropped and no {@link java.util.concurrent.RejectedExecutionException}
     * propagates to HTTP request handlers. If the executor is already shut down the task is
     * discarded and a warning is logged instead.
     */
    static class CallerRunsOrDiscardPolicy implements RejectedExecutionHandler {

        /**
         * Runs the rejected task in the caller's thread, or discards it if the executor has been shut down.
         *
         * @param task the runnable task that was rejected
         * @param executor the executor that rejected the task
         */
        @Override
        public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                log.warn("Event executor is shut down – discarding rejected task: {}", task);
                return;
            }
            log.warn("Event executor at capacity (active={}, queue={}/{}) – running task in caller thread to prevent event loss",
                    executor.getActiveCount(),
                    executor.getQueue().size(),
                    executor.getQueue().size() + executor.getQueue().remainingCapacity());
            task.run();
        }
    }
}
