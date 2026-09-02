package it.eng.dataplane.kafka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot entry point for the Kafka Data Plane service.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"it.eng.dataplane", "it.eng.tools"})
public class DataPlaneKafkaApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(DataPlaneKafkaApplication.class, args);
    }
}
