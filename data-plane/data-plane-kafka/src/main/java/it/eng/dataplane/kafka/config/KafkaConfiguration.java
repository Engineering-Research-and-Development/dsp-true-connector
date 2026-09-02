package it.eng.dataplane.kafka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuration for the Kafka Data Plane.
 */
@Configuration
public class KafkaConfiguration {

    /**
     * Creates the transfer executor used by asynchronous Kafka consumer pulls.
     *
     * @return virtual-thread-per-task executor
     */
    @Bean("transferExecutor")
    public Executor transferExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
