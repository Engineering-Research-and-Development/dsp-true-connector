package it.eng.negotiation.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Spring configuration for negotiation infrastructure beans.
 */
@Configuration
public class NegotiationConfiguration {

    /** Pool size for the task scheduler used by automatic negotiation retry scheduling. */
    @Value("${application.negotiation.scheduler.pool-size:5}")
    private int schedulerPoolSize = 5;

    /**
     * Creates the {@link TaskScheduler} used by {@link it.eng.negotiation.service.AutomaticNegotiationService}
     * to schedule non-blocking retries after a failed protocol message attempt.
     *
     * <p>{@link ThreadPoolTaskScheduler} participates in the Spring lifecycle and shuts down
     * gracefully when the context closes, ensuring scheduled retries are not abandoned mid-flight.
     *
     * <p>A {@link it.eng.tools.configuration.TenantContextTaskDecorator} is installed so that
     * scheduled retry tasks inherit the tenant context from the thread that submitted them.
     *
     * @return configured {@link TaskScheduler}
     */
    @Bean(name = "negotiationTaskScheduler")
    public TaskScheduler negotiationTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(schedulerPoolSize);
        scheduler.setThreadNamePrefix("negotiation-retry-");
        scheduler.setTaskDecorator(new it.eng.tools.configuration.TenantContextTaskDecorator());
        scheduler.initialize();
        return scheduler;
    }
}
