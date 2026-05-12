package it.eng.dataplane.httppush.config;

import it.eng.dataplane.s3.properties.S3Properties;
import it.eng.dataplane.s3.service.S3BucketProvisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuration for the HTTP-PUSH Data Plane.
 * The OkHttpClient bean used by ControlPlaneClient and ControlPlaneRegistrationBean is
 * provided automatically by OkHttpClientConfiguration in tools (component-scanned via
 * the application class). It reads server.ssl.enabled: true = TLS client with custom
 * truststore; false = insecure noop client for development.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class HttpPushConfiguration implements ApplicationListener<ApplicationReadyEvent> {

    private final S3BucketProvisionService s3BucketProvisionService;
    private final S3Properties s3Properties;

    /**
     * Virtual-thread executor for async HTTP-PUSH transfers (Java 21).
     * Each transfer runs on its own virtual thread — no fixed pool ceiling,
     * blocked I/O does not park OS threads, so thousands of concurrent transfers
     * are practical without tuning thread pool sizes.
     *
     * @return virtual-thread-per-task executor
     */
    @Bean("transferExecutor")
    public Executor transferExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Ensures S3 bucket credentials exist in this Data Plane's MongoDB at startup.
     * This mirrors the {@code InitialDataLoader} in the connector — without credentials
     * in the DP's own MongoDB, {@code S3ClientService.generateGetPresignedUrl()} would
     * throw because it needs per-bucket credentials to sign the URL.
     *
     * @param event the application-ready event (unused)
     */
    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        String bucketName = s3Properties.getBucketName();
        if (bucketName != null && !bucketName.isBlank()) {
            try {
                s3BucketProvisionService.ensureBucketCredentials(bucketName);
                log.info("S3 bucket credentials provisioned for bucket '{}' in Data Plane MongoDB", bucketName);
            } catch (Exception e) {
                log.warn("Failed to provision S3 bucket credentials for '{}': {}", bucketName, e.getMessage());
            }
        }
    }
}
