package it.eng.dataplane.s3.startup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Placeholder startup bean for the Data Plane S3 module.
 *
 * <p>Bucket provisioning and authoritative bucket selection are owned by the Control Plane.
 * The Data Plane keeps this bean only to preserve startup wiring without performing any
 * local provisioning work.</p>
 */
@Slf4j
@Component
public class DataPlaneS3StartupBean {

    /**
     * Performs the Data Plane S3 startup hook.
     *
     * <p>Called once after the application context is fully initialized. This hook is an
     * intentional no-op because bucket provisioning is controlled by the Control Plane.</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucketCredentials() {
        log.debug("Skipping Data Plane bucket provisioning at startup; Control Plane owns bucket lifecycle.");
    }
}
