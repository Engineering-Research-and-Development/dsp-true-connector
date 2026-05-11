package it.eng.dataplane.core.startup;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.core.config.DataPlaneProperties;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import it.eng.tools.controller.ApiEndpoints;
import java.io.IOException;
import java.util.Map;

/**
 * Registers this Data Plane with the Control Plane at startup.
 * Retries up to 5 times with exponential backoff (2s base delay).
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

    /**
     * @param properties DP runtime configuration
     * @param registry registered transfer protocol implementations
     * @param okHttpClient TLS-aware HTTP client
     * @param objectMapper shared Jackson mapper
     */
    public ControlPlaneRegistrationBean(DataPlaneProperties properties,
                                        DataTransferProtocolRegistry registry,
                                        OkHttpClient okHttpClient,
                                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.registry = registry;
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
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

    private void registerWithRetry() {
        String url = properties.getControlPlaneAdminEndpoint() + ApiEndpoints.DATA_PLANES;
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("endpoint", properties.getEndpoint());
        payload.put("supportedTransferTypes", registry.getSupportedProtocols());
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
                String adminSecret = properties.getControlPlaneAdminSecret();
                if (adminSecret != null && !adminSecret.isBlank()) {
                    requestBuilder.addHeader("Authorization",
                        okhttp3.Credentials.basic("internal-service", adminSecret));
                }
                Request request = requestBuilder.build();
                try (Response response = okHttpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        log.info("Registered with CP at {} (attempt {})", url, attempt);
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
    }

    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
