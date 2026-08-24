package it.eng.dataplane.grpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot entry point for the gRPC Data Plane service.
 *
 * <p>Component-scans both {@code it.eng.dataplane} and {@code it.eng.tools} packages to
 * auto-configure S3, encryption, and other shared infrastructure from
 * {@code data-plane-core} and {@code s3-support}.</p>
 */
@SpringBootApplication
@ComponentScan(basePackages = {"it.eng.dataplane", "it.eng.tools"})
public class DataPlaneGrpcApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(DataPlaneGrpcApplication.class, args);
    }
}
