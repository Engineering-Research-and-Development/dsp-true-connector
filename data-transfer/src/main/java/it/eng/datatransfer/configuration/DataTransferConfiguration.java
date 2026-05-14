package it.eng.datatransfer.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.TimeUnit;

/**
 * Spring configuration for data-transfer infrastructure beans.
 */
@Configuration
@EnableCaching
public class DataTransferConfiguration {

    /** Pool size for the task scheduler used by automatic transfer retry scheduling. */
    @Value("${application.transfer.scheduler.pool-size:5}")
    private int schedulerPoolSize = 5;

    /**
     * Cache manager for Data Plane routing lookups.
     * Entries expire 10 seconds after write so registration changes propagate quickly.
     *
     * @return caffeine-backed cache manager
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("dataPlanesByType");
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS));
        return manager;
    }

    /**
     * Creates the {@link TaskScheduler} used by {@link it.eng.datatransfer.service.AutomaticDataTransferService}
     * to schedule non-blocking retries after a failed protocol message attempt.
     *
     * <p>{@link ThreadPoolTaskScheduler} participates in the Spring lifecycle and shuts down
     * gracefully when the context closes, ensuring scheduled retries are not abandoned mid-flight.
     *
     * @return configured {@link TaskScheduler}
     */
    @Bean(name = "transferTaskScheduler")
    public TaskScheduler transferTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(schedulerPoolSize);
        scheduler.setThreadNamePrefix("transfer-retry-");
        scheduler.initialize();
        return scheduler;
    }
}

