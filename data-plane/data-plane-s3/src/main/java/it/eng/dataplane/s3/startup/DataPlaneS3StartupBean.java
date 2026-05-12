package it.eng.dataplane.s3.startup;

import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3BucketProvisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ensures bucket credentials are provisioned in the Data Plane's own database on startup.
 *
 * <p>The Control Plane stores bucket credentials in its own MongoDB database. The Data Plane
 * uses a separate database and must provision its own copy of the credentials so that
 * presigned URL generation and S3 operations can work independently.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlaneS3StartupBean {

    private final S3BucketProvisionService s3BucketProvisionService;
    private final S3Properties s3Properties;

    /**
     * Ensures bucket credentials exist in the Data Plane database after startup.
     *
     * <p>Called once after the application context is fully initialized.
     * If credentials already exist (e.g. after a restart), this is a no-op.</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucketCredentials() {
        String bucketName = s3Properties.getBucketName();
        if (bucketName == null || bucketName.isBlank()) {
            log.warn("DataPlaneS3StartupBean: s3.bucketName is not configured — skipping bucket provisioning");
            return;
        }
        log.info("DataPlaneS3StartupBean: ensuring bucket credentials for bucket '{}'", bucketName);
        try {
            s3BucketProvisionService.ensureBucketCredentials(bucketName);
            log.info("DataPlaneS3StartupBean: bucket credentials ready for '{}'", bucketName);
        } catch (Exception e) {
            log.error("DataPlaneS3StartupBean: failed to provision bucket '{}': {}", bucketName, e.getMessage(), e);
        }
    }
}
