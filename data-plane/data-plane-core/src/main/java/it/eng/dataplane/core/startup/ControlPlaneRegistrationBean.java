package it.eng.dataplane.core.startup;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.core.DataPlaneApiEndpoints;
import it.eng.dataplane.core.config.DataPlaneProperties;
import it.eng.dataplane.core.model.DataPlaneAuditEventType;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.service.DataPlaneAuditEventService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registers this Data Plane with the Control Plane at startup and deregisters on shutdown.
 * Registration retries up to 5 times with exponential backoff (2s base delay).
 * Deregistration on {@link PreDestroy} is best-effort: errors are logged but not propagated.
 */
@Slf4j
@Component
public class ControlPlaneRegistrationBean implements ApplicationListener<ApplicationReadyEvent> {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_DELAY_MS = 2_000L;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final DataPlaneProperties properties;
    private final DataTransferProtocolRegistry registry;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final DataPlaneAuditEventService auditEventService;

    /**
     * @param properties DP runtime configuration
     * @param registry registered transfer protocol implementations
     * @param okHttpClient TLS-aware HTTP client
     * @param objectMapper shared Jackson mapper
     * @param auditEventService audit event service for recording registration outcomes
     */
    public ControlPlaneRegistrationBean(DataPlaneProperties properties,
                                        DataTransferProtocolRegistry registry,
                                        OkHttpClient okHttpClient,
                                        ObjectMapper objectMapper,
                                        DataPlaneAuditEventService auditEventService) {
        this.properties = properties;
        this.registry = registry;
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.auditEventService = auditEventService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String cpEndpoint = properties.getControlPlaneAdminEndpoint();
        if (cpEndpoint == null || cpEndpoint.isBlank()) {
            log.warn("dataplane.control-plane-admin-endpoint not set, skipping CP registration");
            return;
        }
        registerWithRetry();
    }

    /**
     * Deregisters this Data Plane from the Control Plane on graceful shutdown.
     * Best-effort: errors are logged but not rethrown so Spring shutdown proceeds normally.
     */
    @PreDestroy
    public void deregisterFromControlPlane() {
        String cpEndpoint = properties.getControlPlaneAdminEndpoint();
        if (cpEndpoint == null || cpEndpoint.isBlank()) {
            return;
        }
        String url = cpEndpoint + DataPlaneApiEndpoints.DATA_PLANES + "/" + properties.getId();
        Request.Builder requestBuilder = new Request.Builder().url(url).delete();
        addAdminAuth(requestBuilder);
        try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
            if (response.isSuccessful()) {
                log.info("Deregistered from CP (id={}, url={})", properties.getId(), url);
                auditEventService.saveEvent(DataPlaneAuditEventType.DP_DEREGISTRATION_SUCCESS,
                        null, null, "Data Plane deregistered from Control Plane",
                        Map.of("controlPlaneUrl", url, "dataplaneId", properties.getId()));
            } else {
                log.warn("Deregistration from CP returned HTTP {} (id={})", response.code(), properties.getId());
                auditEventService.saveEvent(DataPlaneAuditEventType.DP_DEREGISTRATION_FAILED,
                        null, null, "Data Plane deregistration returned HTTP " + response.code(),
                        Map.of("controlPlaneUrl", url, "dataplaneId", properties.getId()));
            }
        } catch (IOException e) {
            log.warn("Deregistration from CP failed: {} (id={})", e.getMessage(), properties.getId());
            auditEventService.saveEvent(DataPlaneAuditEventType.DP_DEREGISTRATION_FAILED,
                    null, null, "Data Plane deregistration failed: " + e.getMessage(),
                    Map.of("controlPlaneUrl", url, "dataplaneId", properties.getId()));
        }
    }

    private void registerWithRetry() {
        String url = properties.getControlPlaneAdminEndpoint() + DataPlaneApiEndpoints.DATA_PLANES;
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", properties.getId());
        payload.put("endpoint", properties.getEndpoint());
        payload.put("supportedTransferTypes", registry.getSupportedProtocols());
        Set<String> transportProfiles = registry.getSupportedProtocols().stream()
                .filter(protocol -> protocol != null && protocol.startsWith("stream:"))
                .collect(Collectors.toSet());
        if (!transportProfiles.isEmpty()) {
            payload.put("transportProfiles", transportProfiles);
        }
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            payload.put("apiKey", properties.getApiKey());
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, JSON))
                    .addHeader("Content-Type", "application/json");
                addAdminAuth(requestBuilder);
                try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
                    if (response.isSuccessful()) {
                        log.info("Registered with CP at {} (id={}, attempt {})", url, properties.getId(), attempt);
                        auditEventService.saveEvent(DataPlaneAuditEventType.DP_REGISTRATION_SUCCESS,
                                null, null, "Data Plane registered with Control Plane",
                                Map.of("controlPlaneUrl", url, "attempt", String.valueOf(attempt)));
                        return;
                    }
                    log.warn("Registration attempt {}/{} rejected HTTP {}", attempt, MAX_ATTEMPTS, response.code());
                }
            } catch (IOException e) {
                log.warn("Registration attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
            }
            if (attempt < MAX_ATTEMPTS) {
                sleep(BASE_DELAY_MS * (long) Math.pow(2, attempt - 1));
            }
        }
        log.error("Failed to register with CP after {} attempts", MAX_ATTEMPTS);
        auditEventService.saveEvent(DataPlaneAuditEventType.DP_REGISTRATION_FAILED,
                null, null, "Data Plane registration with Control Plane failed",
                Map.of("controlPlaneUrl", url, "attempts", String.valueOf(MAX_ATTEMPTS)));
    }

    private void addAdminAuth(Request.Builder requestBuilder) {
        String adminSecret = properties.getControlPlaneAdminSecret();
        if (adminSecret != null && !adminSecret.isBlank()) {
            requestBuilder.addHeader("Authorization",
                okhttp3.Credentials.basic("internal-service", adminSecret));
        }
    }

    /**
     * Sleeps for the specified duration. Protected to allow override in tests.
     *
     * @param ms milliseconds to sleep
     */
    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
